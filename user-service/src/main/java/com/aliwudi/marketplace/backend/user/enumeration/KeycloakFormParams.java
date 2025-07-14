/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.aliwudi.marketplace.backend.user.enumeration;

    public enum KeycloakFormParams {
        CLIENT_ID("client_id"),
        USERNAME("username"),
        PASSWORD("password"),
        GRANT_TYPE("grant_type"),
        TOKEN("token"),
        TOKEN_TYPE_HINT("token_type_hint"),
        REFRESH_TOKEN("refresh_token"),
        REDIRECT_URI("redirect_uri"),
        AUTHORIZATION_CODE("authorization_code");

        private final String paramName;

        KeycloakFormParams(String paramName) {
            this.paramName = paramName;
        }

        public String getParamName() {
            return paramName;
        }
    }
    
