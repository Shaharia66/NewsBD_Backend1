package com.newsbd.service;

import com.newsbd.model.User;
import com.newsbd.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) {
        OAuth2User oAuth2User = super.loadUser(request);
        String provider = request.getClientRegistration().getRegistrationId(); // google | facebook
        Map<String, Object> attrs = oAuth2User.getAttributes();

        String oauthId = String.valueOf(attrs.get("sub") != null ? attrs.get("sub") : attrs.get("id"));
        String email   = (String) attrs.get("email");
        String name    = (String) attrs.get("name");
        String picture = (String) (attrs.get("picture") != null ? attrs.get("picture") : attrs.get("profile_pic"));

        // Upsert user
        User user = userRepository.findByOauthProviderAndOauthId(provider, oauthId)
            .orElseGet(() -> {
                log.info("New OAuth2 user: {} via {}", email, provider);
                return User.builder()
                    .oauthProvider(provider)
                    .oauthId(oauthId)
                    .email(email)
                    .name(name)
                    .pictureUrl(picture)
                    .role(User.Role.USER)
                    .build();
            });

        // Update fields in case they changed
        user.setEmail(email);
        user.setName(name);
        user.setPictureUrl(picture);
        userRepository.save(user);

        return new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
            attrs,
            "email"
        );
    }
}
