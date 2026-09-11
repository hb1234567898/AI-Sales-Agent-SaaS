package com.yourcompany.salesagent.tool.email;

import java.time.Clock;
import java.util.Properties;
import java.util.UUID;

import com.yourcompany.salesagent.shared.security.SecretCipher;
import com.yourcompany.salesagent.shared.security.SecretEncryptionException;
import com.yourcompany.salesagent.tool.email.api.EmailSettingsStatusResponse;
import com.yourcompany.salesagent.tool.email.api.EmailSettingsTestResponse;
import com.yourcompany.salesagent.tool.email.api.EmailSettingsUpdateRequest;
import com.yourcompany.salesagent.tool.email.domain.EmailConfiguration;
import com.yourcompany.salesagent.tool.email.infrastructure.EmailConfigurationMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class EmailConfigurationService {

	private final EmailConfigurationMapper mapper;
	private final SecretCipher secretCipher;
	private final Clock clock;
	private final EmailRuntimeConfiguration environmentConfiguration;

	public EmailConfigurationService(
			EmailConfigurationMapper mapper,
			SecretCipher secretCipher,
			Clock clock,
			@Value("${spring.mail.host:localhost}") String envHost,
			@Value("${spring.mail.port:25}") int envPort,
			@Value("${spring.mail.username:}") String envUsername,
			@Value("${spring.mail.password:}") String envPassword,
			@Value("${app.mail.from:}") String envFrom,
			@Value("${spring.mail.properties.mail.smtp.auth:false}") boolean envSmtpAuth,
			@Value("${spring.mail.properties.mail.smtp.starttls.enable:false}") boolean envStarttlsEnabled,
			@Value("${spring.mail.properties.mail.smtp.starttls.required:false}") boolean envStarttlsRequired) {
		this.mapper = mapper;
		this.secretCipher = secretCipher;
		this.clock = clock;
		this.environmentConfiguration = new EmailRuntimeConfiguration(
				envHost,
				envPort,
				trimToNull(envUsername),
				trimToNull(envPassword),
				trimToNull(envFrom),
				envSmtpAuth,
				envStarttlsEnabled,
				envStarttlsRequired);
	}

	@Transactional(readOnly = true)
	public EmailSettingsStatusResponse status(UUID organizationId) {
		var configuration = mapper.selectById(organizationId);
		if (configuration == null) {
			var ready = environmentConfigured();
			return new EmailSettingsStatusResponse(
					ready ? environmentConfiguration.host() : null,
					ready ? environmentConfiguration.port() : null,
					ready ? environmentConfiguration.username() : null,
					ready ? environmentConfiguration.fromAddress() : null,
					ready && environmentConfiguration.smtpAuth(),
					ready && environmentConfiguration.starttlsEnabled(),
					ready && environmentConfiguration.starttlsRequired(),
					ready && StringUtils.hasText(environmentConfiguration.password()),
					ready,
					ready ? "ENV_FALLBACK" : "MISSING_CONFIGURATION");
		}
		var passwordConfigured = StringUtils.hasText(configuration.getEncryptedPassword());
		var ready = !configuration.isSmtpAuth() || canDecrypt(configuration);
		return new EmailSettingsStatusResponse(
				configuration.getHost(),
				configuration.getPort(),
				configuration.getUsername(),
				configuration.getFromAddress(),
				configuration.isSmtpAuth(),
				configuration.isStarttlsEnabled(),
				configuration.isStarttlsRequired(),
				passwordConfigured,
				ready,
				ready ? "READY" : "ENCRYPTION_KEY_UNAVAILABLE");
	}

	@Transactional
	public EmailSettingsStatusResponse update(UUID organizationId, EmailSettingsUpdateRequest request) {
		var smtpAuth = request.smtpAuth() == null || request.smtpAuth();
		var starttlsEnabled = request.starttlsEnabled() == null || request.starttlsEnabled();
		var starttlsRequired = request.starttlsRequired() != null && request.starttlsRequired();
		var configuration = mapper.selectById(organizationId);
		var encryptedPassword = configuration == null ? null : configuration.getEncryptedPassword();
		if (StringUtils.hasText(request.password())) {
			encryptedPassword = secretCipher.encrypt(organizationId, request.password().strip());
		}
		if (smtpAuth && !StringUtils.hasText(encryptedPassword)) {
			throw new EmailConfigurationException("开启 SMTP 认证时必须输入 SMTP 密码或授权码");
		}
		var now = clock.instant();
		if (configuration == null) {
			mapper.insert(EmailConfiguration.create(
					organizationId,
					request.host().strip(),
					request.port(),
					trimToNull(request.username()),
					encryptedPassword,
					request.fromAddress().strip(),
					smtpAuth,
					starttlsEnabled,
					starttlsRequired,
					now));
		}
		else {
			configuration.update(
					request.host().strip(),
					request.port(),
					trimToNull(request.username()),
					encryptedPassword,
					request.fromAddress().strip(),
					smtpAuth,
					starttlsEnabled,
					starttlsRequired,
					now);
			mapper.updateById(configuration);
		}
		return status(organizationId);
	}

	@Transactional(readOnly = true)
	public EmailSettingsTestResponse testConnection(UUID organizationId) {
		var configuration = requireRuntimeConfiguration(organizationId);
		var startedAt = clock.millis();
		try {
			mailSenderImpl(configuration).testConnection();
			return new EmailSettingsTestResponse("CONNECTED", "SMTP 连接成功", Math.max(0, clock.millis() - startedAt));
		}
		catch (Exception exception) {
			throw new EmailConnectionException("SMTP 连接失败，请检查服务器地址、端口、账号、授权码和 TLS 设置", exception);
		}
	}

	@Transactional(readOnly = true)
	public EmailRuntimeConfiguration requireRuntimeConfiguration(UUID organizationId) {
		var configuration = mapper.selectById(organizationId);
		if (configuration != null) {
			var password = configuration.isSmtpAuth()
					? secretCipher.decrypt(organizationId, configuration.getEncryptedPassword())
					: null;
			return new EmailRuntimeConfiguration(
					configuration.getHost(),
					configuration.getPort(),
					configuration.getUsername(),
					password,
					configuration.getFromAddress(),
					configuration.isSmtpAuth(),
					configuration.isStarttlsEnabled(),
					configuration.isStarttlsRequired());
		}
		if (!environmentConfigured()) {
			throw new EmailConfigurationException("尚未配置发件邮箱，请在设置页保存 SMTP 配置后再发送");
		}
		return environmentConfiguration;
	}

	public JavaMailSender mailSender(EmailRuntimeConfiguration configuration) {
		return mailSenderImpl(configuration);
	}

	private JavaMailSenderImpl mailSenderImpl(EmailRuntimeConfiguration configuration) {
		var sender = new JavaMailSenderImpl();
		sender.setHost(configuration.host());
		sender.setPort(configuration.port());
		if (StringUtils.hasText(configuration.username())) {
			sender.setUsername(configuration.username());
		}
		if (StringUtils.hasText(configuration.password())) {
			sender.setPassword(configuration.password());
		}
		sender.setJavaMailProperties(mailProperties(configuration));
		return sender;
	}

	private boolean environmentConfigured() {
		var localhost = "localhost".equalsIgnoreCase(environmentConfiguration.host())
				|| "127.0.0.1".equals(environmentConfiguration.host());
		return StringUtils.hasText(environmentConfiguration.host())
				&& StringUtils.hasText(environmentConfiguration.fromAddress())
				&& (!localhost || StringUtils.hasText(environmentConfiguration.username()));
	}

	private boolean canDecrypt(EmailConfiguration configuration) {
		if (!configuration.isSmtpAuth()) {
			return true;
		}
		try {
			return StringUtils.hasText(secretCipher.decrypt(
					configuration.getOrganizationId(), configuration.getEncryptedPassword()));
		}
		catch (SecretEncryptionException exception) {
			return false;
		}
	}

	private static Properties mailProperties(EmailRuntimeConfiguration configuration) {
		var properties = new Properties();
		properties.put("mail.smtp.auth", String.valueOf(configuration.smtpAuth()));
		properties.put("mail.smtp.starttls.enable", String.valueOf(configuration.starttlsEnabled()));
		properties.put("mail.smtp.starttls.required", String.valueOf(configuration.starttlsRequired()));
		properties.put("mail.smtp.connectiontimeout", "10000");
		properties.put("mail.smtp.timeout", "10000");
		properties.put("mail.smtp.writetimeout", "10000");
		return properties;
	}

	private static String trimToNull(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return value.strip();
	}
}
