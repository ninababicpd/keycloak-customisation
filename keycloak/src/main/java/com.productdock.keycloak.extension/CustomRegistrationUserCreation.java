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
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.authentication.forms.RegistrationUserCreation;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;
import org.keycloak.userprofile.Attributes;
import org.keycloak.userprofile.UserProfile;
import org.keycloak.userprofile.ValidationException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

import static com.productdock.keycloak.extension.CustomMessages.EMAIL_DOMAIN_NOT_ALLOWED;
import static com.productdock.keycloak.extension.CustomMessages.NETWORK_ISSUES;
import static java.lang.String.format;
import static java.lang.System.getenv;

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
    public void validate(ValidationContext context) {
        log.info("Validating user data");
        List<FormMessage> errors = new java.util.ArrayList<>(Collections.emptyList());
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        context.getEvent().detail(Details.REGISTER_METHOD, "form");

        UserProfile profile = getOrCreateUserProfile(context, formData);

        Attributes attributes = profile.getAttributes();

        String email = attributes.getFirst(UserModel.EMAIL);
        String username = attributes.getFirst(UserModel.USERNAME);
        context.getEvent().detail(Details.EMAIL, email);

        context.getEvent().detail(Details.USERNAME, username);

        if (context.getRealm().isRegistrationEmailAsUsername()) {
            context.getEvent().detail(Details.USERNAME, email);
        }

        try {
            profile.validate();
        } catch (ValidationException pve) {
            errors.addAll(Validation.getFormErrorsFromValidation(pve.getErrors()));

            if (pve.hasError(Messages.EMAIL_EXISTS, Messages.INVALID_EMAIL)) {
                context.getEvent().detail(Details.EMAIL, attributes.getFirst(UserModel.EMAIL));
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
            boolean emailDomainAllowed = emailDomainAllowed(email);

            if (!emailDomainAllowed) {
                log.info("Provided email domain is not allowed");
                context.error(EMAIL_DOMAIN_NOT_ALLOWED);
                errors.add(new FormMessage(null, EMAIL_DOMAIN_NOT_ALLOWED));
                context.validationError(formData, errors);
                return;
            }
        } catch (IOException | URISyntaxException e) {
            log.infof("Error occurred during call to mock API: %s", e.toString());
            context.error(NETWORK_ISSUES);
            errors.add(new FormMessage(null, NETWORK_ISSUES));
            context.validationError(formData, errors);
            return;
        }

        context.success();
    }

    private boolean emailDomainAllowed(String email) throws IOException, URISyntaxException {
        log.info("Checking if provided email domain is allowed.");

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String mockApiUrl = getenv("MOCK_API_URL");

            HttpPost request = new HttpPost(mockApiUrl);
            request.setHeader("Content-Type", "application/json");
            request.setEntity(generateRequestBody(email));

            log.info("Checking email validity.");
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                log.info("Calling mock API to check email validity.");

                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    log.infof("Mock API returned invalid response: %s; with status code: %s",
                            response.getStatusLine().getReasonPhrase(), statusCode);
                    throw new IOException(format("Invalid response from Mock API: %s", statusCode));
                }
                HttpEntity entity = response.getEntity();

                if (entity == null) {
                    return false;
                }

                return domainAllowed(entity);
            }
        }
    }

    private static StringEntity generateRequestBody(String email) throws JsonProcessingException, UnsupportedEncodingException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("email", email);

        String requestBodyJSON = mapper.writeValueAsString(requestBody);
        return new StringEntity(requestBodyJSON);
    }

    private boolean domainAllowed(HttpEntity entity) throws IOException {
        log.info("Successful call to mock API - extracting info if email domain is allowed.");

        String responseBody = EntityUtils.toString(entity);
        JsonNode responseJson = objectMapper.readTree(responseBody);
        if (responseJson == null || !responseJson.has(DOMAIN_ALLOWED)) {
            return false;
        }
        return responseJson.path(DOMAIN_ALLOWED).asBoolean();
    }

    @Override
    public void success(FormContext context) {
        super.success(context);
    }

    @Override
    public void buildPage(FormContext context, LoginFormsProvider form) {

    }

}
