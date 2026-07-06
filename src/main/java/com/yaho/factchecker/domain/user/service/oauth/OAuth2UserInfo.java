package com.yaho.factchecker.domain.user.service.oauth;

import java.util.Map;

public interface OAuth2UserInfo {
    String getProvider();
    String getEmail();
    String getName();
    Map<String, Object> getAttributes();
}