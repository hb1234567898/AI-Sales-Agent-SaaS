package com.yourcompany.salesagent.opportunity.domain;

public enum OpportunityStage {
	QUALIFIED, DISCOVERY, DEMO, PROPOSAL, NEGOTIATION, WON, LOST;

	public boolean closed() { return this == WON || this == LOST; }
}
