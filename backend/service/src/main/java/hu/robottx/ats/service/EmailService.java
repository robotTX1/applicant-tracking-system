package hu.robottx.ats.service;

import hu.robottx.ats.entity.Application;

public interface EmailService {

    void sendApplicationDecisionEmail(Application application);

}
