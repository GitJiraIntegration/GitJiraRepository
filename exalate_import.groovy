import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.user.ApplicationUser;
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import org.apache.commons.io.IOUtils;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import com.atlassian.jira.security.roles.ProjectRole;
import com.atlassian.jira.bc.projectroles.ProjectRoleService;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.jira.util.SimpleErrorCollection;
import com.atlassian.jira.user.util.UserManager;

JsonSlurper jsonSlurper = new JsonSlurper();
ProjectManager projectManager = ComponentAccessor.getProjectManager();
ProjectRoleManager projectRoleManager = ComponentAccessor.getComponent(ProjectRoleManager.class);
ProjectRoleService projectRoleService = ComponentAccessor.getComponent(ProjectRoleService.class);
GroupManager groupManager = ComponentAccessor.getGroupManager();
SimpleErrorCollection simpleErrorCollection = new SimpleErrorCollection();
UserManager userManager = ComponentAccessor.getUserManager();

List<String> projectRoleIds = new ArrayList<String>();

//ProjectRolesIds in Build
//Administrator
projectRoleIds.add("10002");
//Developers
projectRoleIds.add("10100");
//Users
projectRoleIds.add("10301");
//Read Only
projectRoleIds.add("10300");
//Service Desk Customers
projectRoleIds.add("10200");
//Service Desk Team	
projectRoleIds.add("10201");

//Your JIRA login
String authorizationString = "<username>:<password>";

//The Project
String projectKeyBuild = "EMS";
String projectKeyDigital = "";

//Source JIRA Instance
String JIRA_URL = "https://jira.build.ingka.ikea.com/"

Project projectDigital = projectManager.getProjectByCurrentKey(projectKeyDigital);

StringBuilder sb = new StringBuilder();

for(String projectRoleId : projectRoleIds){
    
    String JIRA_API_URL = JIRA_URL + "rest/api/2/project/" + projectKeyBuild + "/role/" + projectRoleId;

    URLConnection conn = JIRA_API_URL.toURL().openConnection();
	
    conn.addRequestProperty("Authorization", "Basic ${authorizationString.bytes.encodeBase64().toString()}");
	conn.addRequestProperty("Content-Type", "application/json");
	conn.setRequestMethod("GET");
	conn.doOutput = false;
	conn.connect();
	String stringResponse = IOUtils.toString(conn.getInputStream(), "UTF-8");
    
    stringResponse = JsonOutput.prettyPrint(stringResponse);

	def response = jsonSlurper.parseText(stringResponse);
    
    String projectRoleName = response.name;
    ProjectRole projectRole = projectRoleManager.getProjectRole(projectRoleName);
    
    for(int i = 0; i < response.actors.size(); i++){
        String actorName = response.actors[i].name;
        String actorType = response.actors[i].type;
                
        if(actorType.equals("atlassian-group-role-actor")){
            List<String> group = new ArrayList<String>();
            group.add(actorName);
            
            projectRoleService.addActorsToProjectRole(group, projectRole, projectDigital, "atlassian-group-role-actor", simpleErrorCollection);
        }
        else if(actorType.equals("atlassian-user-role-actor")){
            ApplicationUser actor = userManager.getUserByName(actorName);
            List<String> user = new ArrayList<String>();
            
            if(actor != null){
                String actorKey = actor.getKey();
            	user.add(actorKey);
            }
            else{
                log.warn(actorName + " doesn't exist in your JIRA instance");
            }
            projectRoleService.addActorsToProjectRole(user, projectRole, projectDigital, "atlassian-user-role-actor", simpleErrorCollection);
        }
        else{
            log.warn(actorName + "is not a user or group, probably corrupt data");
        }
    }
}