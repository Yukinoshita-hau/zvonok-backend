package com.zvonok.integrationtest.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.stream.Stream;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zvonok.model.User;
import com.zvonok.service.UserService;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
public class AuthControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("zvonok-test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserService userService;

    @Autowired
    private MockMvc mockMvc;

    private String REGISTER_URL = "/auth/register";
    private String LOGIN_URL = "/auth/login";
    private String REFRESH_URL = "/auth/refresh";
    private String LOGOUT_URL = "/auth/logout";

    private String testUsername;
    private String testEmail;
    private String testPassword;
    private String testRefreshToken;
    private static String testInvalidRefreshToken = "fdd752c5-69e4-4756-a732-08d1728a9cc2g1mocX01MjefXxUFgHuz-4g"; 
    private String testAccessToken;

    
    @BeforeEach
    void setUp() throws Exception{
        testUsername = "testuser_" + System.currentTimeMillis();
        testEmail = testUsername + "@test.com";
        testPassword = "testpassword";

        mockMvc.perform(post(REGISTER_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(registerRequestJson(testUsername, testEmail, testPassword)));
    
        MvcResult loginResult = mockMvc.perform(post(LOGIN_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginRequestJson(testUsername, testPassword)))
            .andReturn();
    
        testRefreshToken = extractRefreshToken(loginResult);
        testAccessToken = extractAccessToken(loginResult); 
    }

    @AfterEach
    void tearDown() {
        User user = userService.getUser(testUsername);
        userService.deleteUser(user.getId());
    }



    // Register
    static String registerRequestJson(String username, String email, String password) {
        return """
            {
                "username": "%s",
                "email": "%s",
                "password": "%s"                  
            }         
        """.formatted(username, email, password);
    }

    @Test
    void register_shouldReturns200AndTokens_whenValidData() throws Exception {

        // Act
        mockMvc.perform(post(REGISTER_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(registerRequestJson("testuser", "test@test.ru", "testpassword")))
        // Assert
            .andExpect(status().isOk())
            .andExpectAll(
                jsonPath("$.accessToken").exists(),
                jsonPath("$.refreshToken").exists(),
                jsonPath("$.accessToken").isNotEmpty(), 
                jsonPath("$.refreshToken").isNotEmpty()
            );
    }

    @Test
    void register_shouldReturn409AndErrorMessage_whenExistUserData() throws Exception {
        // Act
        mockMvc.perform(post(REGISTER_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(registerRequestJson(testUsername, testEmail, testPassword)))
        // Assert
            .andExpect(status().isConflict())
            .andExpectAll(
                jsonPath("$.message").exists(),
                jsonPath("$.status").exists()
            );
    }

    @ParameterizedTest
    @MethodSource("registerReturn400Cases")
    void register_shouldReturn400AndErrorMessage(String errorMessage, String content) throws Exception {
        // Act 
        mockMvc.perform(post(REGISTER_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(content))
        // Assert
            .andExpect(status().isBadRequest())
            .andExpectAll(
                jsonPath("$.status").value(400),
                jsonPath("$.message").value(errorMessage)
            );
    }

    static private Stream<Arguments> registerReturn400Cases() {
        return Stream.of(
            Arguments.of(
                "username: Username is required",
                """
                    {
                        "email": "%s",
                        "password": "%s"                  
                    }         
                """.formatted("testEmail@test.ru", "testPassword")
            ),
            Arguments.of(
                "email: Email is required",
                """
                    {
                        "username": "%s",
                        "password": "%s"                  
                    }         
                """.formatted("testUsername", "testPassword")
            ),
            Arguments.of(
                "password: Password is required",
                """
                    {
                        "username": "%s",
                        "email": "%s"
                    }         
                """.formatted("testUsername", "testEmail@test.ru")
            ),
            // length
            Arguments.of(
                "username: Username must be between 3 and 32 characters",
                registerRequestJson("12", "testEmail@test.ru", "testPassword")
            ),
            Arguments.of(
                "email: Email must be between 5 and 100 characters",
                registerRequestJson("testUsername", "1@23", "testPassword")
            ),
            Arguments.of(
                "password: Password must be at least 6 characters",
                registerRequestJson("testUsername", "testEmail@test.ru", "12345")
            ),
            // Invalid format
            Arguments.of(
                "email: Invalid email format",
                registerRequestJson("testUserame", "testUsername.ru", "testPassword")
            )
        );
    }

    // Login
    static String loginRequestJson(String usernameOrEmail, String password) {
        return """
            {
                "usernameOrEmail": "%s",
                "password": "%s"                 
            }          
        """.formatted(usernameOrEmail, password);
    }

    private String extractAccessToken(MvcResult result) throws Exception {
        String response = result.getResponse().getContentAsString();
        return new ObjectMapper().readTree(response).get("accessToken").asText();
    }

    @Test
    void login_shouldReturn200AndToken_whenValidData() throws Exception {
        // Act
        mockMvc.perform(post(LOGIN_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginRequestJson(testUsername, testPassword)))
        // Assert
            .andExpect(status().isOk())
            .andExpectAll(
                jsonPath("$.accessToken").exists(),
                jsonPath("$.refreshToken").exists(),
                jsonPath("$.accessToken").isNotEmpty(),
                jsonPath("$.refreshToken").isNotEmpty()
            );
    } 

    @Test
    void login_shouldReturn401AndErrorMessage_whenInvalidData() throws Exception {
        // Act
        mockMvc.perform(post(LOGIN_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginRequestJson("invalidUsernameOrEmail", "invalidPassword")))
        // Assert
            .andExpect(status().isUnauthorized())
            .andExpectAll(
                jsonPath("$.status").value(401),
                jsonPath("$.message").value("Invalid user or password")
            );
    }
    
    @ParameterizedTest
    @MethodSource("loginReturn400Cases")
    void login_shouldReturn400AndErrorMessage(String errorMessage, String content) throws Exception {
        
        // Act
        mockMvc.perform(post(LOGIN_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(content))
        // Assert
            .andExpect(status().isBadRequest())
            .andExpectAll(
                jsonPath("$.status").value(400),
                jsonPath("$.message").value(errorMessage)
            );
    } 

    static private Stream<Arguments> loginReturn400Cases() {
        return Stream.of(
            Arguments.of(
                "usernameOrEmail: Username or Email is required",
                """
                    {
                        "password": "testPassword"  
                    }
                """
            ),
            Arguments.of(
                "password: Password is required",
                """
                    {
                        "usernameOrEmail": "testUsernameOrEmail"  
                    }
                """
            ),
            Arguments.of(
                "usernameOrEmail: Username or email must be between 5 and 100 characters",
                loginRequestJson("1234", "testPassword")
            ),
            Arguments.of(
                "password: Password must be between 6 and 100 characters",
                loginRequestJson("testUsernameOrEmail", "12345")
            )
        );    
    }

    // Refresh
    static String refreshRequestJson(String refreshToken) {
        return """
            {
                "refreshToken":"%s" 
            }         
        """.formatted(refreshToken);
    }

    private String extractRefreshToken(MvcResult result) throws Exception {
        String response = result.getResponse().getContentAsString();
        return new ObjectMapper().readTree(response).get("refreshToken").asText();    
    }

    @Test
    void refresh_shouldReturn200AndNewValidToken_whenValidData() throws Exception {
        // Act
        mockMvc.perform(post(REFRESH_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(refreshRequestJson(testRefreshToken)))
        // Assert
            .andExpect(status().isOk())
            .andExpectAll(
                jsonPath("$.accessToken").exists(),
                jsonPath("$.refreshToken").exists(),
                jsonPath("$.accessToken").isNotEmpty(),
                jsonPath("$.refreshToken").isNotEmpty()
            ); 
    }

    @Test
    void refresh_shouldReturn401AndErrorMessage_whenInvalidData() throws Exception {
        // Act
        mockMvc.perform(post(REFRESH_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(refreshRequestJson(
                testInvalidRefreshToken
            )))
        // Assert
            .andExpect(status().isUnauthorized())
            .andExpectAll(
                jsonPath("$.status").value(401),
                jsonPath("$.message").value("Refresh token is invalid")
            ); 
    }

    @ParameterizedTest
    @MethodSource("refreshReturn400Cases")
    void refresh_shouldReturn400AndErrorMessage(String errorMessage, String content) throws Exception {
        // Act
        mockMvc.perform(post(REFRESH_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .content(content))
        // Assert
            .andExpect(status().isBadRequest())
            .andExpectAll(
                jsonPath("$.status").value(400),
                jsonPath("$.message").value(errorMessage)
            );
    }

    static private Stream<Arguments> refreshReturn400Cases() {
        return Stream.of(
            Arguments.of(
                "refreshToken: RefreshToken is required",
                """
                {

                }          
                """
            ),
            Arguments.of(
                "refreshToken: RefreshToken must be 59 characters",
                refreshRequestJson("fdd752c5-69e4-4756-a732-08d1728a9cc2g1mocX01MjefXxUFgHuz-4")
            ),
            Arguments.of(
                "refreshToken: RefreshToken must be 59 characters",
                refreshRequestJson("fdd752c5-69e4-4756-a732-08d1728a9cc2g1mocX01MjefXxUFgHuz-4gg")
            )
        );
    }

    // Logout
    static String logoutRequestJson(String refreshToken, boolean allDevices) {
        return """
            {
                "refreshToken":"%s",
                "allDevices": %b
            }         
        """.formatted(refreshToken, allDevices);
    }


    @Test
    void logout_shouldReturn200AndMessage_whenValidDataAndAllDevicesFalse() throws Exception {
        // Act
        mockMvc.perform(post(LOGOUT_URL)
            .header("Authorization", "Bearer " + testAccessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(logoutRequestJson(testRefreshToken, false)))
        // Assert
            .andExpect(status().isOk())
            .andExpectAll(
                jsonPath("$.message").value("Logout successful"),
                jsonPath("$.allDevices").value(false)
            );
    }

    @Test
    void logout_shouldReturn200AndMessage_whenValidDataAndAllDevicesTrue() throws Exception {
        // Act
        mockMvc.perform(post(LOGOUT_URL)
            .header("Authorization", "Bearer " + testAccessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(logoutRequestJson(testRefreshToken, true)))
        // Assert
            .andExpect(status().isOk())
            .andExpectAll(
                jsonPath("$.message").value("Logout successful"),
                jsonPath("$.allDevices").value(true)
            );
    }

    @Test
    void logout_shouldReturn401AndErrorMessage_whenInvalidDataAndAllDevicesFalse() throws Exception {
        // Act
        mockMvc.perform(post(LOGOUT_URL)
            .header("Authorization", "Bearer " + testAccessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(logoutRequestJson(testInvalidRefreshToken, false)))
        // Assert
            .andExpect(status().isUnauthorized())
            .andExpectAll(
                jsonPath("$.status").value(401),
                jsonPath("$.message").value("Refresh token is invalid")
            );
    }

    @Test
    void logout_shouldReturn401AndErrorMessage_whenInvalidDataAndAllDevicesTrue() throws Exception {
        // Act
        mockMvc.perform(post(LOGOUT_URL)
            .header("Authorization", "Bearer " + testAccessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(logoutRequestJson(testInvalidRefreshToken, true)))
        // Assert
            .andExpect(status().isUnauthorized())
            .andExpectAll(
                jsonPath("$.status").value(401),
                jsonPath("$.message").value("Refresh token is invalid")
            );
    }

    @Test
    void logout_shouldReturn403AndErrorMessage_whenJwtTokenInvalid() throws Exception {
        // Act
        mockMvc.perform(post(LOGOUT_URL)
            .header("Authorization", "Bearer " + "testJwtToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content(logoutRequestJson(testInvalidRefreshToken, false)))
        // Assert
            .andExpect(status().isForbidden())
            .andExpectAll(
                jsonPath("$.status").value(403),
                jsonPath("$.message").value("JWT token not valid or missing!")
            );
    }

    @Test
    void logout_shouldReturn403AndErrorMessage_whenJwtTokenMissing() throws Exception {
        // Act
        mockMvc.perform(post(LOGOUT_URL)
            .header("Authorization", "Bearer " + "")
            .contentType(MediaType.APPLICATION_JSON)
            .content(logoutRequestJson(testInvalidRefreshToken, false)))
        // Assert
            .andExpect(status().isForbidden())
            .andExpectAll(
                jsonPath("$.status").value(403),
                jsonPath("$.message").value("JWT token not valid or missing!")
            );
    }

    @Test
    void logout_shouldReturn401AndErrorMessage_whenRefreshTokenIsRevoked() throws Exception {
        // Arrange
        mockMvc.perform(post(LOGOUT_URL)
            .header("Authorization", "Bearer " + testAccessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(logoutRequestJson(testRefreshToken, false)));

        // Act
        mockMvc.perform(post(LOGOUT_URL)
            .header("Authorization", "Bearer " + testAccessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(logoutRequestJson(testRefreshToken, false)))
        // Assert
            .andExpect(status().isUnauthorized())
            .andExpectAll(
                jsonPath("$.status").value(401),
                jsonPath("$.message").value("Refresh token is revoked")
            );
    }

    @ParameterizedTest
    @MethodSource("logoutReturn400Cases")
    void logout_shouldReturn400(String errorMessage, String content) throws Exception {
        // Arrange
        mockMvc.perform(post(LOGOUT_URL)
            .header("Authorization", "Bearer " + testAccessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(content))
        // Act
            .andExpect(status().isBadRequest())
            .andExpectAll(
                jsonPath("$.status").value(400),
                jsonPath("$.message").value(errorMessage)
            );
    }

    static private Stream<Arguments> logoutReturn400Cases() {
        return Stream.of(
            Arguments.of(
                "refreshToken: RefreshToken is required",
                """
                    {
                        "allDevices": false 
                    }         
                """
            ),
            Arguments.of(
                "refreshToken: RefreshToken must be 59 characters",
                logoutRequestJson("fdd752c5-69e4-4756-a732-08d1728a9cc2g1mocX01MjefXxUFgHuz-4", false)
            ),
            Arguments.of(
                "refreshToken: RefreshToken must be 59 characters",
                logoutRequestJson("fdd752c5-69e4-4756-a732-08d1728a9cc2g1mocX01MjefXxUFgHuz-4gg", false)
            ),
            Arguments.of(
                "allDevices must be boolean",
                """
                    {
                        "refreshToken":"%s",
                        "allDevices": 0                        
                    }         
                """.formatted(AuthControllerIT.testInvalidRefreshToken)
            ),
            Arguments.of(
                "allDevices must be boolean",
                """
                    {
                        "refreshToken":"%s",
                        "allDevices": 1                        
                    }         
                """.formatted(AuthControllerIT.testInvalidRefreshToken)
            ),      
            Arguments.of(
                "allDevices must be boolean",
                """
                    {
                        "refreshToken":"%s",
                        "allDevices": ""                        
                    }         
                """.formatted(AuthControllerIT.testInvalidRefreshToken)
            ),
            Arguments.of(
                "allDevices must be boolean",
                """
                    {
                        "refreshToken":"%s",
                        "allDevices": "false"                        
                    }         
                """.formatted(AuthControllerIT.testInvalidRefreshToken)
            )
        );
    }
}