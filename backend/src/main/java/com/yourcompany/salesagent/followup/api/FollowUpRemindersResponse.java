package com.yourcompany.salesagent.followup.api;

import java.util.List;
import com.yourcompany.salesagent.followup.infrastructure.FollowUpReminderRow;

public record FollowUpRemindersResponse(long unreadCount, List<FollowUpReminderRow> items) {
}
