package com.yourcompany.salesagent.shared.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

@Configuration
@MapperScan({
		"com.yourcompany.salesagent.admin.infrastructure",
		"com.yourcompany.salesagent.agent.infrastructure",
		"com.yourcompany.salesagent.assistant.infrastructure",
		"com.yourcompany.salesagent.ai.infrastructure",
		"com.yourcompany.salesagent.approval.infrastructure",
		"com.yourcompany.salesagent.audit.infrastructure",
		"com.yourcompany.salesagent.auth.infrastructure",
		"com.yourcompany.salesagent.customer.infrastructure",
		"com.yourcompany.salesagent.followup.infrastructure",
		"com.yourcompany.salesagent.interaction.infrastructure",
		"com.yourcompany.salesagent.tool.infrastructure"
})
public class MybatisPlusConfiguration {

	@Bean
	MybatisPlusInterceptor mybatisPlusInterceptor() {
		var interceptor = new MybatisPlusInterceptor();
		interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
		interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
		return interceptor;
	}
}
