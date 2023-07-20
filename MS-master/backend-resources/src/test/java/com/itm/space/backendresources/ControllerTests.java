package com.itm.space.backendresources;

import com.itm.space.backendresources.api.request.UserRequest;
import com.itm.space.backendresources.controller.RestExceptionHandler;
import com.itm.space.backendresources.exception.BackendResourcesException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.MethodArgumentNotValidException;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "user", password = "root", authorities = "ROLE_MODERATOR")
public class ControllerTests extends BaseIntegrationTest {

    @MockBean
    private Keycloak keycloak;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestExceptionHandler restExceptionHandler;

    @Value("${keycloak.realm}")
    private String realmItm;

    private UserRequest testUserRequest;
    private UserRequest testInvalidUserRequest;
    private RealmResource realmResourceMock;
    private UsersResource usersResourceMock;
    private UserRepresentation userRepresentationMock;
    private UserResource userResourceMock;

    @BeforeEach
    void initNecessaryMocks() {
        testUserRequest = new UserRequest("thor", "thor@gmail.com", "root", "ken", "floor");
        testInvalidUserRequest = new UserRequest("", "thor@gmail.com", "root", "ken", "floor");
        realmResourceMock = mock(RealmResource.class);
        usersResourceMock = mock(UsersResource.class);
        userRepresentationMock = mock(UserRepresentation.class);
        userResourceMock = mock(UserResource.class);
    }

    @Test
    public void helloMethodTest_ShouldReturnOk() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/users/hello")).andReturn().getResponse();
        assertEquals(HttpStatus.OK.value(), response.getStatus());
        assertEquals("user", response.getContentAsString());
    }

    @Test
    @SneakyThrows
    public void userCreatedTest_ShouldReturnSuccessStatus() {
        when(keycloak.realm(realmItm)).thenReturn(realmResourceMock);
        when(realmResourceMock.users()).thenReturn(usersResourceMock);
        when(usersResourceMock.create(any())).thenReturn(Response.status(Response.Status.CREATED).build());
        when(userRepresentationMock.getId()).thenReturn(UUID.randomUUID().toString());
        MockHttpServletResponse response = mvc.perform(requestWithContent(post("/api/users"), testUserRequest))
                .andReturn().getResponse();
        assertEquals(HttpStatus.OK.value(), response.getStatus());
        verify(keycloak).realm(realmItm);
        verify(realmResourceMock).users();
        verify(usersResourceMock).create(any(org.keycloak.representations.idm.UserRepresentation.class));
    }

    @Test
    public void getUserByIdTest_ShouldReturnUserIDSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        when(keycloak.realm(realmItm)).thenReturn(realmResourceMock);
        when(realmResourceMock.users()).thenReturn(mock(UsersResource.class));
        when(realmResourceMock.users().get(eq(String.valueOf(userId)))).thenReturn(userResourceMock);
        when(userResourceMock.toRepresentation()).thenReturn(userRepresentationMock);
        when(userRepresentationMock.getId()).thenReturn(String.valueOf(userId));
        MockHttpServletResponse response = mockMvc.perform(get("/api/users/{id}", userId))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse();
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatus());
    }

    @Test
    @SneakyThrows
    public void createUser_ShouldCatchHandleExceptionTest() {
        BackendResourcesException exception = new BackendResourcesException("Backend resources exception occurred",
                HttpStatus.INTERNAL_SERVER_ERROR);
        ResponseEntity<String> exceptionResponse = restExceptionHandler.handleException(exception);
        when(keycloak.realm(realmItm)).thenReturn(realmResourceMock);
        when(realmResourceMock.users()).thenReturn(usersResourceMock);
        when(usersResourceMock.create(any())).thenReturn(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
        when(userRepresentationMock.getId()).thenReturn(UUID.randomUUID().toString());
        MockHttpServletResponse response = mvc.perform(requestWithContent(post("/api/users"), testUserRequest))
                .andReturn().getResponse();
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exceptionResponse.getStatusCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatus());
        assertEquals("Backend resources exception occurred", exceptionResponse.getBody());
    }

    @Test
    @SneakyThrows
    public void userCreatedTest_ShouldHandleInvalidArgument() {
        Map<String, String> errorMap = new HashMap<>();
        try {
            MockHttpServletResponse response = mvc.perform(requestWithContent(post("/api/users"),
                    testInvalidUserRequest)).andReturn().getResponse();
            assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatus());
        } catch (MethodArgumentNotValidException ex) {
            ex.getBindingResult().getFieldErrors()
                    .forEach(error -> errorMap.put(error.getField(), error.getDefaultMessage()));

            Map<String, String> response = restExceptionHandler.handleInvalidArgument(ex);
            assertEquals(2, response.size());
            assertTrue(response.containsKey("name"));
            assertTrue(response.containsKey("email"));
            assertEquals("Name is required", response.get("name"));
            assertEquals("Invalid email format", response.get("email"));
        }
    }
}
