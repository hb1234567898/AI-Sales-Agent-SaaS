package com.yourcompany.salesagent.customer.infrastructure;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yourcompany.salesagent.customer.domain.Customer;
import com.yourcompany.salesagent.customer.domain.CustomerContact;
import com.yourcompany.salesagent.customer.domain.CustomerSource;
import com.yourcompany.salesagent.customer.domain.CustomerStage;
import com.yourcompany.salesagent.customer.domain.CustomerStatus;

@Component
@ConditionalOnProperty(name = "app.demo.seed-enabled", havingValue = "true", matchIfMissing = true)
public class DemoCustomerDataInitializer implements ApplicationRunner {

	private static final UUID USER_CHEN = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID USER_LI = UUID.fromString("10000000-0000-0000-0000-000000000002");
	private static final UUID USER_WANG = UUID.fromString("10000000-0000-0000-0000-000000000003");
	private static final UUID MEMBER_CHEN = UUID.fromString("20000000-0000-0000-0000-000000000001");
	private static final UUID MEMBER_LI = UUID.fromString("20000000-0000-0000-0000-000000000002");
	private static final UUID MEMBER_WANG = UUID.fromString("20000000-0000-0000-0000-000000000003");

	private final DemoDataMapper demoDataMapper;
	private final CustomerMapper customerMapper;
	private final CustomerContactMapper contactMapper;
	private final Clock clock;
	private final PasswordEncoder passwordEncoder;
	private final UUID organizationId;
	private final String loginPassword;

	public DemoCustomerDataInitializer(
			DemoDataMapper demoDataMapper,
			CustomerMapper customerMapper,
			CustomerContactMapper contactMapper,
			Clock clock,
			PasswordEncoder passwordEncoder,
			@Value("${app.demo.organization-id}") UUID organizationId,
			@Value("${app.demo.login-password}") String loginPassword) {
		this.demoDataMapper = demoDataMapper;
		this.customerMapper = customerMapper;
		this.contactMapper = contactMapper;
		this.clock = clock;
		this.passwordEncoder = passwordEncoder;
		this.organizationId = organizationId;
		this.loginPassword = loginPassword;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		demoDataMapper.insertOrganization(organizationId);
		demoDataMapper.insertUser(USER_CHEN, "chen.mo@demo.local", "陈默", "demo-chen");
		demoDataMapper.insertUser(USER_LI, "li.xin@demo.local", "李昕", "demo-li");
		demoDataMapper.insertUser(USER_WANG, "wang.ning@demo.local", "王宁", "demo-wang");
		demoDataMapper.insertMember(MEMBER_CHEN, organizationId, USER_CHEN);
		demoDataMapper.insertMember(MEMBER_LI, organizationId, USER_LI);
		demoDataMapper.insertMember(MEMBER_WANG, organizationId, USER_WANG);
		var passwordHash = passwordEncoder.encode(loginPassword);
		demoDataMapper.insertCredential(USER_CHEN, passwordHash);
		demoDataMapper.insertCredential(USER_LI, passwordHash);
		demoDataMapper.insertCredential(USER_WANG, passwordHash);

		if (customerMapper.selectCount(Wrappers.<Customer>lambdaQuery()
				.eq(Customer::getOrganizationId, organizationId)
				.isNull(Customer::getDeletedAt)) > 0) {
			return;
		}

		var customers = List.of(
				new DemoCustomer("云岚科技", "企业服务", CustomerStage.PROPOSAL, MEMBER_CHEN, 92, "486000", "发送方案确认邮件", "林婉清", "lin@yunlan.demo", "13800000001", Duration.ofMinutes(18)),
				new DemoCustomer("恒川智造", "智能制造", CustomerStage.DISCOVERY, MEMBER_LI, 88, "762000", "确认技术评审时间", "周启明", "zhou@hengchuan.demo", "13800000002", Duration.ofHours(1)),
				new DemoCustomer("北辰零售", "新零售", CustomerStage.DEMO, MEMBER_CHEN, 84, "318000", "跟进试用反馈", "宋雨", "song@beichen.demo", "13800000003", Duration.ofHours(19)),
				new DemoCustomer("澄海数据", "数据服务", CustomerStage.QUALIFIED, MEMBER_WANG, 76, "224000", "补充 ROI 测算", "许哲", "xu@chenghai.demo", "13800000004", Duration.ofHours(24)),
				new DemoCustomer("拓维物流", "智慧物流", CustomerStage.QUALIFIED, MEMBER_LI, 71, "196000", "重新确认采购窗口", "韩知远", "han@tuowei.demo", "13800000005", Duration.ofDays(2)),
				new DemoCustomer("星桥教育", "职业教育", CustomerStage.LEAD, MEMBER_WANG, 63, "128000", "发送行业案例", "沈禾", "shen@xingqiao.demo", "13800000006", Duration.ofDays(3)),
				new DemoCustomer("沐光医疗", "医疗科技", CustomerStage.LEAD, MEMBER_CHEN, 58, "273000", "确认预算周期", "苏恬", "su@muguang.demo", "13800000007", Duration.ofDays(5)));

		customers.forEach(this::insertCustomer);
	}

	private void insertCustomer(DemoCustomer demo) {
		var now = clock.instant();
		var attributes = new HashMap<String, Object>();
		attributes.put("score", demo.score());
		attributes.put("estimatedValue", new BigDecimal(demo.estimatedValue()));
		attributes.put("nextAction", demo.nextAction());

		var customer = Customer.create(organizationId, demo.name(), now);
		customer.update(
				demo.name(),
				null,
				demo.industry(),
				null,
				demo.stage(),
				CustomerStatus.ACTIVE,
				CustomerSource.MANUAL,
				demo.ownerId(),
				now.plus(Duration.ofDays(1)),
				attributes,
				now);
		customer.recordInteractionAt(now.minus(demo.lastInteractionAgo()));
		customerMapper.insert(customer);

		contactMapper.insert(CustomerContact.primary(
				organizationId,
				customer.getId(),
				demo.contactName(),
				demo.contactEmail(),
				demo.contactPhone(),
				now));
	}

	private record DemoCustomer(
			String name,
			String industry,
			CustomerStage stage,
			UUID ownerId,
			int score,
			String estimatedValue,
			String nextAction,
			String contactName,
			String contactEmail,
			String contactPhone,
			Duration lastInteractionAgo) {
	}
}
