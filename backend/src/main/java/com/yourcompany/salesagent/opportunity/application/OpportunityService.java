package com.yourcompany.salesagent.opportunity.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.opportunity.api.*;
import com.yourcompany.salesagent.opportunity.domain.OpportunityStage;
import com.yourcompany.salesagent.opportunity.infrastructure.*;

@Service
public class OpportunityService {
	private final OpportunityMapper mapper;
	private final Clock clock;
	public OpportunityService(OpportunityMapper mapper, Clock clock) { this.mapper = mapper; this.clock = clock; }
	public IPage<OpportunityResponse> find(AuthPrincipal actor, String query, OpportunityStage stage, UUID owner, int page, int size) {
		return mapper.selectPage(new Page<>(page + 1L, size), actor.organizationId(), query.strip(), stage, owner)
				.convert(OpportunityResponse::from);
	}
	public OpportunityResponse get(AuthPrincipal actor, UUID id) { return OpportunityResponse.from(require(actor, id)); }
	public OpportunityMetricsResponse metrics(AuthPrincipal actor) { return mapper.selectMetrics(actor.organizationId(), clock.instant()); }
	public List<OpportunityEventResponse> history(AuthPrincipal actor, UUID id) {
		require(actor, id);
		return mapper.selectEvents(actor.organizationId(), id);
	}
	@Transactional
	public OpportunityResponse create(AuthPrincipal actor, OpportunityUpsertRequest input) {
		validateReferences(actor, input);
		var id = UUID.randomUUID();
		var now = clock.instant();
		mapper.insert(actor.organizationId(), id, input, now);
		mapper.insertEvent(actor.organizationId(), id, actor.memberId(), null, OpportunityStage.QUALIFIED, "创建商机", now);
		return get(actor, id);
	}
	@Transactional
	public OpportunityResponse update(AuthPrincipal actor, UUID id, OpportunityUpsertRequest input) {
		var row = require(actor, id);
		requireOpen(row);
		if (!row.getCustomerId().equals(input.customerId())) throw new OpportunityWorkflowException("商机关联客户不能更换");
		validateReferences(actor, input);
		if (input.version() == null) throw new OpportunityWorkflowException("请携带商机版本后重试");
		var now = clock.instant();
		if (mapper.update(actor.organizationId(), id, input, now) != 1) throw conflict();
		mapper.insertEvent(actor.organizationId(), id, actor.memberId(), row.getStage(), row.getStage(),
				"更新商机资料：" + input.name() + "；金额 " + input.amount() + "；成交概率 " + input.probability() + "%", now);
		return get(actor, id);
	}
	@Transactional
	public OpportunityResponse changeStage(AuthPrincipal actor, UUID id, OpportunityStageRequest input) {
		var row = require(actor, id);
		requireOpen(row);
		if (row.getStage() == input.stage()) throw new OpportunityWorkflowException("商机已经处于该阶段");
		if (input.stage() == OpportunityStage.LOST && (input.reason() == null || input.reason().isBlank()))
			throw new OpportunityWorkflowException("请填写输单原因");
		int probability = switch (input.stage()) {
			case QUALIFIED -> 20;
			case DISCOVERY -> 30;
			case DEMO -> 45;
			case PROPOSAL -> 60;
			case NEGOTIATION -> 80;
			case WON -> 100;
			case LOST -> 0;
		};
		var now = clock.instant();
		if (mapper.changeStage(actor.organizationId(), id, input, probability, now) != 1) throw conflict();
		mapper.insertEvent(actor.organizationId(), id, actor.memberId(), row.getStage(), input.stage(),
				"变更销售阶段" + (input.reason() == null || input.reason().isBlank() ? "" : "：" + input.reason()), now);
		return get(actor, id);
	}
	private OpportunityRow require(AuthPrincipal actor, UUID id) {
		var row = mapper.selectOne(actor.organizationId(), id);
		if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商机不存在或已被删除");
		return row;
	}
	private static void requireOpen(OpportunityRow row) {
		if (!"OPEN".equals(row.getStatus())) throw new OpportunityWorkflowException("已关闭的商机不能修改");
	}
	private void validateReferences(AuthPrincipal actor, OpportunityUpsertRequest input) {
		if (mapper.customerExists(actor.organizationId(), input.customerId()) != 1)
			throw new OpportunityWorkflowException("客户不存在、已停用或不属于当前团队");
		if (mapper.ownerExists(actor.organizationId(), input.ownerMemberId()) != 1)
			throw new OpportunityWorkflowException("负责人不存在、已停用或不属于当前团队");
	}
	private static OptimisticLockingFailureException conflict() {
		return new OptimisticLockingFailureException("商机已更新，请刷新后重试");
	}
}
