/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.usergrid.rest.applications.collection.groups;


import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.jersey.api.client.UniformInterfaceException;
import org.apache.usergrid.rest.test.resource2point0.AbstractRestIT;
import org.apache.usergrid.rest.test.resource2point0.model.ApiResponse;
import org.apache.usergrid.rest.test.resource2point0.model.Collection;
import org.apache.usergrid.rest.test.resource2point0.model.Entity;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.usergrid.cassandra.Concurrent;

import static org.junit.Assert.*;

/** @author rockerston */
@Concurrent()
public class GroupResourceIT extends AbstractRestIT {
    private static Logger log = LoggerFactory.getLogger( GroupResourceIT.class );

    public GroupResourceIT() throws Exception { }

    private Entity createGroup(String groupName, String groupPath) throws IOException{
        Entity payload = new Entity();
        payload.put("name", groupName);
        payload.put("path", groupPath);
        Entity entity = this.app().collection("groups").post(payload);
        assertEquals(entity.get("name"), groupName);
        assertEquals(entity.get("path"), groupPath);
        this.refreshIndex();
        return entity;
    }

    private Entity createRole(String roleName, String roleTitle) throws IOException{
        Entity payload = new Entity();
        payload.put("name", roleName);
        payload.put("title", roleTitle);
        Entity entity = this.app().collection("roles").post(payload);
        assertEquals(entity.get("name"), roleName);
        assertEquals(entity.get("title"), roleTitle);
        this.refreshIndex();
        return entity;
    }

    private Entity createUser(String username, String email) throws IOException{
        Entity payload = new Entity();
        payload.put("username", username);
        payload.put("email", email);
        Entity entity = this.app().collection("users").post(payload);
        assertEquals(entity.get("username"), username);
        assertEquals(entity.get("email"), email);
        this.refreshIndex();
        return entity;
    }

    /***
     *
     * Verify that we can create a group with a standard string in the name and path
     */
    @Test()
    public void createGroupValidation() throws IOException {

        String groupName = "testgroup";
        String groupPath = "testgroup";
        this.createGroup(groupName, groupPath);

    }

    /***
     *
     * Verify that we can create a group with a slash in the name and path
     */

    @Test()
    public void createGroupSlashInNameAndPathValidation() throws IOException {

        String groupNameSlash = "test/group";
        String groupPathSlash = "test/group";
        this.createGroup(groupNameSlash, groupPathSlash);

    }

    /***
     *
     * Verify that we can create a group with a space in the name
     */

    @Test()
    public void createGroupSpaceInNameValidation() throws IOException {

        String groupSpaceName = "test group";
        String groupPath = "testgroup";
        this.createGroup(groupSpaceName, groupPath);

    }

    /***
     *
     * Verify that we cannot create a group with a space in the path
     */

    @Test()
    public void createGroupSpaceInPathValidation() throws IOException {

        String groupName = "testgroup";
        String groupSpacePath = "test group";
        try {
            Entity group = this.createGroup(groupName, groupSpacePath);
            fail("Should not be able to create a group with a space in the path");
        } catch (UniformInterfaceException e) {
            //verify the correct error was returned
            JsonNode node = mapper.readTree( e.getResponse().getEntity( String.class ));
            assertEquals( "illegal_argument", node.get( "error" ).textValue() );
        }

    }

    /***
     *
     * Verify that we can create a group, change the name, then delete it
     */
    @Test()
    public void changeGroupNameValidation() throws IOException {

        //1. create a group
        String groupName = "testgroup";
        String groupPath = "testgroup";
        Entity group = this.createGroup(groupName, groupPath);

        //2. change the name
        String newGroupPath = "newtestgroup";
        group.put("path", newGroupPath);
        Entity groupResponse = this.app().collection("groups").entity(group).put(group);
        assertEquals(groupResponse.get("path"), newGroupPath);
        this.refreshIndex();

        //3. do a GET to verify the property really was set
        Entity groupResponseGET = this.app().collection("groups").entity(group).get();
        assertEquals(groupResponseGET.get("path"), newGroupPath);

        //4. now delete the group
        ApiResponse response = this.app().collection("groups").entity(group).delete();
        //todo: what to do with delete responses?

        //5. do a GET to make sure the entity was deleted
        try {
            this.app().collection("groups").uniqueID(groupName).get();
            fail("Entity still exists");
        } catch (UniformInterfaceException e) {
            //verify the correct error was returned
            JsonNode node = mapper.readTree( e.getResponse().getEntity( String.class ));
            assertEquals( "service_resource_not_found", node.get( "error" ).textValue() );
        }

    }

    /***
     *
     * Verify that we can create a group, user, add user to group, delete connection
     */
    @Test()
    public void addRemoveUserGroup() throws IOException {

        //1. create a group
        String groupName = "testgroup";
        String groupPath = "testgroup";
        Entity group = this.createGroup(groupName, groupPath);

        // 2. create a user
        String username = "fred";
        String email = "fred@usergrid.com";
        Entity user = this.createUser(username, email);

        // 3. add the user to the group
        Entity response = this.app().collection("users").entity(user).connection().collection("groups").entity(group).post();
        assertEquals(response.get("name"), groupName);
        this.refreshIndex();

        // 4. make sure the user is in the group
        Collection collection = this.app().collection("groups").entity(group).connection().collection("users").get();
        Entity entity = collection.next();
        assertEquals(entity.get("username"), username);

        //5. try it the other way around
        collection = this.app().collection("users").entity(user).connection().collection("groups").get();
        entity = collection.next();
        assertEquals(entity.get("name"), groupName);

        //6. remove the user from the group
        ApiResponse responseDel = this.app().collection("group").entity(group).connection().collection("users").entity(user).delete();
        this.refreshIndex();
        //todo: how to check response from delete

        //6. make sure the connection no longer exists
        collection = this.app().collection("group").entity(group).connection().collection("users").get();
        assertEquals(collection.hasNext(), false);

        //8. do a GET to make sure the user still exists and did not get deleted with the collection delete
        Entity userEntity = this.app().collection("user").entity(user).get();
        assertEquals(userEntity.get("username"), username);

    }

    /***
     *
     * Verify that we can create a group, role, add role to group, delete connection
     */
    @Test
    public void addRemoveRoleGroup() throws IOException {

        //1. create a group
        String groupName = "testgroup";
        String groupPath = "testgroup";
        Entity group = this.createGroup(groupName, groupPath);

        //2. create a role
        String roleName = "tester";
        String roleTitle = "tester";
        Entity role = this.createRole(roleName, roleTitle);

        //3. add role to the group
        Entity response = this.app().collection("role").entity(role).connection().collection("groups").entity(group).post();
        assertEquals(response.get("name"), roleName);
        this.refreshIndex();

        //4. make sure the role is in the group
        Collection collection = this.app().collection("groups").entity(group).connection().collection("roles").get();
        Entity entity = collection.next();
        assertEquals(entity.get("name"), roleName);

        //5. remove Role from the group (should only delete the connection)
        ApiResponse responseDel = this.app().collection("role").entity(role).connection().collection("groups").entity(group).delete();
        this.refreshIndex();
        //todo: how to check response from delete

        //6. make sure the connection no longer exists
        collection = this.app().collection("groups").entity(group).connection().collection("roles").get();
        try {
            collection.next();
            fail("Entity still exists");
        } catch (UniformInterfaceException e) {
            //verify the correct error was returned
            JsonNode node = mapper.readTree( e.getResponse().getEntity( String.class ));
            assertEquals( "service_resource_not_found", node.get( "error" ).textValue() );
        }

        //7. check root roles to make sure role still exists
        role = this.app().collection("roles").uniqueID(roleName).get();
        assertEquals(role.get("name"), roleName);

        //8. delete the role
        ApiResponse responseDel2 = this.app().collection("role").entity(role).delete();
        this.refreshIndex();
        //todo: what to do with response from delete call?

        //9. do a GET to make sure the role was deleted
        try {
            this.app().collection("role").entity(role).get();
            fail("Entity still exists");
        } catch (UniformInterfaceException e) {
            //verify the correct error was returned
            JsonNode node = mapper.readTree( e.getResponse().getEntity( String.class ));
            assertEquals( "service_resource_not_found", node.get( "error" ).textValue() );
        }

    }


    /***
     *
     * Verify that group / role permissions work
     *
     *  create group
     *  create user
     *  create role
     *  add permissions to role (e.g. POST, GET on /cats)
     *  add role to group
     *  add user to group
     *  delete default role (to ensure no app-level user operations are allowed)
     *  delete guest role (to ensure no app-level user operations are allowed)
     *  create a /cats/fluffy
     *  read /cats/fluffy
     *  update /cats/fluffy (should fail)
     *  delete /cats/fluffy (should fail)
     */
    @Test()
    public void addRolePermissionToGroupVerifyPermission() throws IOException {

        //1. create a group
        String groupName = "testgroup";
        String groupPath = "testgroup";
        Entity group = this.createGroup(groupName, groupPath);

        //2. create a user
        String username = "fred";
        String email = "fred@usergrid.com";
        Entity user = this.createUser(username, email);

        //3. create a role
        String roleName = "tester";
        String roleTitle = "tester";
        Entity role = this.createRole(roleName, roleTitle);

        //4. add permissions to role


        //5. add role to the group
        Entity addRoleresponse = this.app().collection("role").entity(role).connection().collection("groups").entity(group).post();
        assertEquals(addRoleresponse.get("name"), roleName);
        this.refreshIndex();

        //6. add user to group
        Entity addUserResponse = this.app().collection("users").entity(user).connection().collection("groups").entity(group).post();
        assertEquals(addUserResponse.get("name"), groupName);
        this.refreshIndex();
    }

    /***
     *
     * Verify that we can create a group and then add a role to it
     */
    @Test()
    public void createGroupAndAddARoleValidation() throws IOException {

        /*
        JsonNode node = mapper.readTree( resource().path( "/test-organization/test-app/roles" ).queryParam( "access_token", access_token )
                .accept( MediaType.APPLICATION_JSON ).type( MediaType.APPLICATION_JSON_TYPE ).get( String.class ));
        assertNull( node.get( "errors" ) );
        assertTrue( node.get( "entities" ).findValuesAsText( "name" ).contains( roleName ) );

        */
    }

    /*

    @Test
    public void addRemovePermission() throws IOException {

        GroupsCollection groups = context.groups();



        UUID id = UUIDUtils.newTimeUUID();

        String groupName = "groupname" + id;

        ApiResponse response = client.createGroup( groupName );
        assertNull( "Error was: " + response.getErrorDescription(), response.getError() );

        refreshIndex("test-organization", "test-app");

        UUID createdId = response.getEntities().get( 0 ).getUuid();

        // add Permission
        String orgName = context.getOrgName();
        String appName = context.getAppName();
        String path = "/"+orgName+"/"+appName+"/groups/";

        String json = "{\"permission\":\"delete:/test\"}";
        JsonNode node = mapper.readTree( resource().path( path + createdId + "/permissions" )
                .queryParam( "access_token", access_token ).accept( MediaType.APPLICATION_JSON )
                .type( MediaType.APPLICATION_JSON_TYPE ).post( String.class, json ));

        // check it
        assertNull( node.get( "errors" ) );
        assertEquals( node.get( "data" ).get( 0 ).asText(), "delete:/test" );

        refreshIndex("test-organization", "test-app");

        node = mapper.readTree( resource().path( "/test-organization/test-app/groups/" + createdId + "/permissions" )
                .queryParam( "access_token", access_token ).accept( MediaType.APPLICATION_JSON )
                .type( MediaType.APPLICATION_JSON_TYPE ).get( String.class ));
        assertNull( node.get( "errors" ) );
        assertEquals( node.get( "data" ).get( 0 ).asText(), "delete:/test" );


        // remove Permission

        node = mapper.readTree( resource().path( "/test-organization/test-app/groups/" + createdId + "/permissions" )
                .queryParam( "access_token", access_token ).queryParam( "permission", "delete%3A%2Ftest" )
                .accept( MediaType.APPLICATION_JSON ).type( MediaType.APPLICATION_JSON_TYPE ).delete( String.class ));

        // check it
        assertNull( node.get( "errors" ) );
        assertTrue( node.get( "data" ).size() == 0 );

        refreshIndex("test-organization", "test-app");

        node = mapper.readTree( resource().path( "/test-organization/test-app/groups/" + createdId + "/permissions" )
                .queryParam( "access_token", access_token ).accept( MediaType.APPLICATION_JSON )
                .type( MediaType.APPLICATION_JSON_TYPE ).get( String.class ));
        assertNull( node.get( "errors" ) );
        assertTrue( node.get( "data" ).size() == 0 );
    }


    /***
     *
     * Post a group activity
     */

    @Test
    public void postGroupActivity() throws IOException {

        /*

        //1. create a group
        GroupsCollection groups = context.groups();

        //create a group with a normal name
        String groupName = "groupTitle";
        String groupPath = "groupPath";
        JsonNode testGroup = groups.create(groupName, groupPath);
        //verify the group was created
        assertNull(testGroup.get("errors"));
        assertEquals(testGroup.get("path").asText(), groupPath);

        //2. post group activity

        //TODO: actually post a group activity
        */
    }


}