package com.productdock.keycloak.extension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auto.service.AutoService;
import jakarta.ws.rs.core.MultivaluedMap;
import lombok.extern.jbosslog.JBossLog;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.authentication.forms.RegistrationUserCreation;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.messages.Messages;
import org.keycloak.userprofile.Attributes;
import org.keycloak.userprofile.UserProfile;
import org.keycloak.userprofile.ValidationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.productdock.keycloak.extension.CustomMessages.EMAIL_DOMAIN_NOT_ALLOWED;
import static com.productdock.keycloak.extension.CustomMessages.INTERNAL_SERVER_ERROR;
import static java.lang.System.getenv;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.keycloak.models.UserModel.EMAIL;
import static org.keycloak.models.UserModel.USERNAME;
import static org.keycloak.models.utils.FormMessage.GLOBAL;
import static org.keycloak.services.validation.Validation.getFormErrorsFromValidation;

@JBossLog
@AutoService(FormActionFactory.class)
public class CustomRegistrationUserCreation extends RegistrationUserCreation {

    private static final String ID = "custom-registration-user-creation";
    private static final String DOMAIN_ALLOWED = "domainAllowed";

    ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayType() {
        return "Custom Registration - with email domain check";
    }

    @Override
    public String getHelpText() {
        return "This action must always be first! Validates the username and user profile of the user in validation phase. It also validates email and checks if email domain is allowed. In success phase, this will create the user in the database including his user profile.";
    }

    @Override
    public void validate(ValidationContext context) {
        log.info("Validating user data");
        List<FormMessage> errors = new ArrayList<>();

        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        context.getEvent().detail(Details.REGISTER_METHOD, "form");

        UserProfile profile = getOrCreateUserProfile(context, formData);
        Attributes attributes = profile.getAttributes();

        String email = attributes.getFirst(EMAIL);
        String username = attributes.getFirst(USERNAME);

        context.getEvent().detail(Details.EMAIL, email);
        context.getEvent().detail(Details.USERNAME, username);

        if (context.getRealm().isRegistrationEmailAsUsername()) {
            context.getEvent().detail(Details.USERNAME, email);
        }

        try {
            profile.validate();
        } catch (ValidationException pve) {
            errors.addAll(getFormErrorsFromValidation(pve.getErrors()));

            if (pve.hasError(Messages.EMAIL_EXISTS, Messages.INVALID_EMAIL)) {
                context.getEvent().detail(Details.EMAIL, attributes.getFirst(EMAIL));
            }

            if (pve.hasError(Messages.EMAIL_EXISTS)) {
                context.error(Errors.EMAIL_IN_USE);
            } else if (pve.hasError(Messages.USERNAME_EXISTS)) {
                context.error(Errors.USERNAME_IN_USE);
            } else {
                context.error(Errors.INVALID_REGISTRATION);
            }

            context.validationError(formData, errors);
            return;
        }

        try {
            if (!emailDomainAllowed(email)) {
                log.info("Provided email domain is not allowed");
                context.error(EMAIL_DOMAIN_NOT_ALLOWED);
                errors.add(new FormMessage(GLOBAL, EMAIL_DOMAIN_NOT_ALLOWED));
                context.validationError(formData, errors);
                return;
            }
        } catch (IOException e) {
            log.infof("Unexpected Error occurred during call to Mock API: %s", e.toString());
            context.error(INTERNAL_SERVER_ERROR);
            errors.add(new FormMessage(GLOBAL, INTERNAL_SERVER_ERROR));
            context.validationError(formData, errors);
            return;
        }

        context.success();
    }

    private boolean emailDomainAllowed(String email) throws IOException {
        log.info("Checking if provided email domain is allowed.");

        HttpPost request = new HttpPost(getMockApiUrl());
        request.setHeader("Content-Type", "application/json");
        request.setEntity(generateRequestBody(email));

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            log.info("Calling mock API to check email validity.");
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    log.infof("Mock API returned invalid response: %s; with status code: %s",
                            response.getStatusLine().getReasonPhrase(), statusCode);
                    throw new MockAPIException("Invalid response from Mock API.");
                }

                HttpEntity responseBody = response.getEntity();
                if (responseBody == null) {
                    log.info("Mock API returned invalid response. Response body is missing");
                    throw new MockAPIException("Invalid response from Mock API.");
                }
                return domainAllowed(responseBody);
            }
        }
    }

    private String getMockApiUrl() {
        String mockApiUrl = getenv("MOCK_API_URL");
        if (isEmpty(mockApiUrl)) {
            throw new IllegalArgumentException("MOCK_API_URL is not set.");
        }
        return mockApiUrl;
    }

    private static StringEntity generateRequestBody(String email) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("email", email);
        String requestBodyJSON = mapper.writeValueAsString(requestBody);
        return new StringEntity(requestBodyJSON, APPLICATION_JSON);
    }

    private boolean domainAllowed(HttpEntity entity) throws IOException {
        log.info("Successful call to mock API - extracting info if email domain is allowed.");
        String responseBody = EntityUtils.toString(entity);
        JsonNode responseJson = objectMapper.readTree(responseBody);
        if (responseJson == null || !responseJson.has(DOMAIN_ALLOWED)) {
            throw new MockAPIException("Domain validation failed: DOMAIN_ALLOWED field is missing or null.");
        }
        return responseJson.path(DOMAIN_ALLOWED).asBoolean();
    }
}
