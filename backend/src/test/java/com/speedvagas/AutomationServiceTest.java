package com.speedvagas;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutomationServiceTest {
    @Test void duplicateIsSkipped(){
        assertEquals(AutomationService.Decision.SKIP_DUPLICATE,AutomationService.decideCandidate(true,false,true,true,95,true,85,3));
    }
    @Test void badMatchFeedbackIsSkipped(){
        assertEquals(AutomationService.Decision.SKIP_BAD_MATCH,AutomationService.decideCandidate(false,true,true,true,95,true,85,3));
    }
    @Test void highScoreCanAutoSendOnlyWhenEnabledAndQuotaExists(){
        assertEquals(AutomationService.Decision.AUTO_SEND,AutomationService.decideCandidate(false,false,true,true,92,true,85,2));
        assertEquals(AutomationService.Decision.DRAFT,AutomationService.decideCandidate(false,false,true,true,92,false,85,2));
        assertEquals(AutomationService.Decision.DRAFT,AutomationService.decideCandidate(false,false,true,true,92,true,85,0));
    }
    @Test void lowerScoreBecomesDraft(){
        assertEquals(AutomationService.Decision.DRAFT,AutomationService.decideCandidate(false,false,true,true,82,true,85,3));
    }
    @Test void noEmailButPortalRequiresHumanAction(){
        assertEquals(AutomationService.Decision.ACTION_REQUIRED,AutomationService.decideCandidate(false,false,false,true,90,true,85,3));
    }
    @Test void noEmailAndNoPortalHasNoChannel(){
        assertEquals(AutomationService.Decision.NO_CHANNEL,AutomationService.decideCandidate(false,false,false,false,90,true,85,3));
    }
}
