package com.yourcompany.salesagent.lead.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.UUID;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.lead.api.LeadAssignRequest;
import com.yourcompany.salesagent.lead.api.LeadConversionResponse;
import com.yourcompany.salesagent.lead.api.LeadConvertRequest;
import com.yourcompany.salesagent.lead.api.LeadImportRequest;
import com.yourcompany.salesagent.lead.api.LeadImportResponse;
import com.yourcompany.salesagent.lead.api.LeadMetricsResponse;
import com.yourcompany.salesagent.lead.api.LeadResponse;
import com.yourcompany.salesagent.lead.api.LeadUpsertRequest;
import com.yourcompany.salesagent.lead.domain.LeadStatus;
import com.yourcompany.salesagent.lead.infrastructure.LeadMapper;
import com.yourcompany.salesagent.customer.api.OwnerOptionResponse;

@Service
public class LeadService {
	private final LeadMapper mapper;
	private final Clock clock;

	public LeadService(LeadMapper mapper, Clock clock) {
		this.mapper = mapper;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public IPage<LeadResponse> findLeads(AuthPrincipal actor, String query, LeadStatus status, int page, int size) {
		var rows = mapper.selectLeads(Page.of(page + 1L, size), actor.organizationId(), trimToNull(query), status);
		return new Page<LeadResponse>(rows.getCurrent(), rows.getSize(), rows.getTotal())
				.setRecords(rows.getRecords().stream().map(LeadResponse::from).toList());
	}

	@Transactional(readOnly = true)
	public LeadResponse findLead(AuthPrincipal actor, UUID leadId) {
		return LeadResponse.from(requireLead(actor.organizationId(), leadId));
	}

	@Transactional(readOnly = true)
	public LeadMetricsResponse metrics(AuthPrincipal actor) {
		var row = mapper.selectMetrics(actor.organizationId());
		return new LeadMetricsResponse(row.getPending(), row.getQualified(), row.getUnassigned(), row.getAverageScore());
	}

	@Transactional(readOnly = true)
	public List<OwnerOptionResponse> owners(AuthPrincipal actor) {
		return mapper.selectOwners(actor.organizationId());
	}

	@Transactional
	public LeadResponse createLead(AuthPrincipal actor, LeadUpsertRequest request) {
		validateEditableStatus(request.status());
		validateOwner(actor.organizationId(), request.ownerMemberId());
		validateDuplicate(actor.organizationId(), null, request);
		return create(actor.organizationId(), request);
	}

	@Transactional
	public LeadResponse updateLead(AuthPrincipal actor, UUID leadId, LeadUpsertRequest request) {
		validateEditableStatus(request.status());
		requireLead(actor.organizationId(), leadId);
		validateOwner(actor.organizationId(), request.ownerMemberId());
		validateDuplicate(actor.organizationId(), leadId, request);
		var now = clock.instant();
		if (mapper.updateLead(actor.organizationId(), leadId, request.company().strip(), trimToNull(request.industry()),
				request.source(), request.status(), request.ownerMemberId(), request.score(), trimToNull(request.nextAction()),
				request.nextFollowUpAt(), now) == 0) {
			throw new LeadWorkflowException("已转化或已失效的线索不能继续编辑");
		}
		mapper.updateContact(actor.organizationId(), leadId, request.contactName().strip(),
				normalizeEmail(request.contactEmail()), trimToNull(request.contactPhone()),
				trimToNull(request.contactTitle()), now);
		return findLead(actor, leadId);
	}

	@Transactional
	public LeadResponse assign(AuthPrincipal actor, UUID leadId, LeadAssignRequest request) {
		requireLead(actor.organizationId(), leadId);
		validateOwner(actor.organizationId(), request.ownerMemberId());
		if (mapper.assignLead(actor.organizationId(), leadId, request.ownerMemberId(), clock.instant()) == 0) {
			throw new LeadWorkflowException("该线索当前不能分配");
		}
		return findLead(actor, leadId);
	}

	@Transactional
	public LeadConversionResponse convert(AuthPrincipal actor, UUID leadId, LeadConvertRequest request) {
		var lead = requireLead(actor.organizationId(), leadId);
		if (lead.getOwnerMemberId() == null) {
			throw new LeadWorkflowException("请先为线索分配负责人，再转为商机");
		}
		var now = clock.instant();
		if (mapper.convertLead(actor.organizationId(), leadId, now) == 0) {
			throw new LeadWorkflowException("该线索已经转化或已失效");
		}
		var opportunityId = UUID.randomUUID();
		mapper.insertOpportunity(opportunityId, actor.organizationId(), leadId, lead.getOwnerMemberId(),
				request.opportunityName().strip(), request.amount(), request.expectedCloseDate(), now);
		return new LeadConversionResponse(leadId, leadId, opportunityId);
	}

	@Transactional
	public LeadImportResponse importLeads(AuthPrincipal actor, LeadImportRequest request) {
		var errors = new ArrayList<String>();
		var created = 0;
		for (var index = 0; index < request.leads().size(); index++) {
			var lead = request.leads().get(index);
			try {
				validateEditableStatus(lead.status());
				validateOwner(actor.organizationId(), lead.ownerMemberId());
				validateDuplicate(actor.organizationId(), null, lead);
				create(actor.organizationId(), lead);
				created++;
			}
			catch (LeadWorkflowException exception) {
				errors.add("第 " + (index + 1) + " 行：" + exception.getMessage());
			}
		}
		return new LeadImportResponse(request.leads().size(), created, request.leads().size() - created, errors);
	}

	private LeadResponse create(UUID organizationId, LeadUpsertRequest request) {
		var leadId = UUID.randomUUID();
		var now = clock.instant();
		mapper.insertLead(leadId, organizationId, request.company().strip(), trimToNull(request.industry()),
				request.source(), request.status(), request.ownerMemberId(), request.score(),
				trimToNull(request.nextAction()), request.nextFollowUpAt(), now);
		mapper.insertContact(UUID.randomUUID(), organizationId, leadId, request.contactName().strip(),
				normalizeEmail(request.contactEmail()), trimToNull(request.contactPhone()),
				trimToNull(request.contactTitle()), request.source(), now);
		return LeadResponse.from(mapper.selectLead(organizationId, leadId));
	}

	private com.yourcompany.salesagent.lead.infrastructure.LeadRow requireLead(UUID organizationId, UUID leadId) {
		var lead = mapper.selectLead(organizationId, leadId);
		if (lead == null) throw new LeadWorkflowException("线索不存在或已被删除");
		return lead;
	}

	private void validateOwner(UUID organizationId, UUID ownerMemberId) {
		if (ownerMemberId != null && mapper.ownerExists(organizationId, ownerMemberId) == 0) {
			throw new LeadWorkflowException("负责人不属于当前组织或账号已停用");
		}
	}

	private void validateDuplicate(UUID organizationId, UUID excludeId, LeadUpsertRequest request) {
		if (mapper.countDuplicates(organizationId, excludeId, request.company().strip(),
				normalizeEmail(request.contactEmail()), trimToNull(request.contactPhone())) > 0) {
			throw new LeadWorkflowException("发现相同企业、邮箱或电话，请先核对现有客户和线索");
		}
	}

	private static void validateEditableStatus(LeadStatus status) {
		if (status == LeadStatus.QUALIFIED) {
			throw new LeadWorkflowException("请使用线索转化功能创建客户和商机");
		}
	}

	private static String normalizeEmail(String value) {
		return StringUtils.hasText(value) ? value.strip().toLowerCase() : null;
	}

	private static String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.strip() : null;
	}
}
