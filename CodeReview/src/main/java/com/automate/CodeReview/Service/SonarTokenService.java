package com.automate.CodeReview.Service;
import org.springframework.stereotype.Service;

@Service
public class SonarTokenService {
    private volatile String currentToken;
    public void updateToken(String token){ this.currentToken = token; }
    public String getToken(){ return currentToken; }
    public boolean isSet(){ return currentToken != null && !currentToken.isBlank(); }
}

