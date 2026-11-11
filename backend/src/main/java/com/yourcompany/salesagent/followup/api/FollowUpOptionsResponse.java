package com.yourcompany.salesagent.followup.api;

import java.util.List;
import com.yourcompany.salesagent.followup.infrastructure.FollowUpChoiceRow;

public record FollowUpOptionsResponse(List<FollowUpChoiceRow> customers, List<FollowUpChoiceRow> owners) {
}
