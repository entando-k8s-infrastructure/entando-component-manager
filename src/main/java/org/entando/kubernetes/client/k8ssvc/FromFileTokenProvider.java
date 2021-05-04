package org.entando.kubernetes.client.k8ssvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.Getter;
import org.entando.kubernetes.exception.EntandoComponentManagerException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.client.resource.UserApprovalRequiredException;
import org.springframework.security.oauth2.client.resource.UserRedirectRequiredException;
import org.springframework.security.oauth2.client.token.AccessTokenProvider;
import org.springframework.security.oauth2.client.token.AccessTokenRequest;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2RefreshToken;

public class FromFileTokenProvider implements AccessTokenProvider {

    @Getter
    private final Path tokenFileUri;
    private OAuth2AccessToken oauth2AccessToken;

    public FromFileTokenProvider(Path tokenFileUri) {
        this.tokenFileUri = tokenFileUri;
        this.oauth2AccessToken = this.readOAuth2AccessToken();
    }

    @Override
    public OAuth2AccessToken obtainAccessToken(OAuth2ProtectedResourceDetails oauth2ProtectedResourceDetails,
            AccessTokenRequest accessTokenRequest)
            throws UserRedirectRequiredException, UserApprovalRequiredException, AccessDeniedException {

        return this.oauth2AccessToken;
    }

    @Override
    public boolean supportsResource(OAuth2ProtectedResourceDetails oauth2ProtectedResourceDetails) {
        return true;
    }

    @Override
    public OAuth2AccessToken refreshAccessToken(OAuth2ProtectedResourceDetails oauth2ProtectedResourceDetails,
            OAuth2RefreshToken oauth2RefreshToken, AccessTokenRequest accessTokenRequest)
            throws UserRedirectRequiredException {

        this.oauth2AccessToken = this.readOAuth2AccessToken();
        return this.oauth2AccessToken;
    }

    @Override
    public boolean supportsRefresh(OAuth2ProtectedResourceDetails oauth2ProtectedResourceDetails) {
        return true;
    }

    /**
     * read the token from the set file.
     * @return the OAuth2AccessToken built using the token read from the set file
     */
    private OAuth2AccessToken readOAuth2AccessToken() {
        try {
            return new DefaultOAuth2AccessToken(Files.readString(this.getTokenFileUri()).trim());
        } catch (IOException e) {
            throw new EntandoComponentManagerException(String
                    .format("Issues retrieving service account token from %s", this.tokenFileUri), e);
        }
    }
}
