package com.yourcompany.salesagent.assistant.application;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.yourcompany.salesagent.assistant.api.AssistantChatRequest;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;

@Component
public class AssistantStreamTransport {
	private static final Logger log = LoggerFactory.getLogger(AssistantStreamTransport.class);

	private final AssistantChatService service;
	private final Semaphore slots = new Semaphore(8);
	private final java.util.concurrent.ExecutorService workers = Executors.newFixedThreadPool(8);
	private final java.util.concurrent.ScheduledExecutorService heartbeats = Executors.newScheduledThreadPool(1);

	public AssistantStreamTransport(AssistantChatService service) {
		this.service = service;
	}

	public SseEmitter open(AuthPrincipal principal, AssistantChatRequest request) {
		if (!slots.tryAcquire()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "助手繁忙，请稍后再试");
		var emitter = new SseEmitter(600_000L);
		var disconnected = new AtomicBoolean();
		emitter.onCompletion(() -> disconnected.set(true));
		emitter.onTimeout(() -> { disconnected.set(true); emitter.complete(); });
		emitter.onError(error -> disconnected.set(true));
		BiConsumer<String, Object> send = (name, data) -> {
			if (disconnected.get()) return;
			try {
				emitter.send(SseEmitter.event().name(name).data(data));
			} catch (IOException | IllegalStateException exception) {
				disconnected.set(true);
			}
		};
		try {
			workers.execute(new DelegatingSecurityContextRunnable(() -> {
				ScheduledFuture<?> heartbeat = null;
				try {
					var conversationId = service.beginStream(principal, request);
					send.accept("meta", Map.of("conversationId", conversationId));
					heartbeat = heartbeats.scheduleAtFixedRate(
							() -> send.accept("ping", Map.of()), 15, 15, TimeUnit.SECONDS);
					// 断开显示连接不重放或回滚已提交的业务动作，结果仍存入会话历史。
					service.streamChat(principal, conversationId, request.message(), send);
				} catch (AssistantWorkflowException exception) {
					send.accept("error", Map.of("message", exception.getMessage()));
				} catch (RuntimeException exception) {
					log.warn("MCP assistant stream failed before completion", exception);
					send.accept("error", Map.of("message", "会话初始化或结果保存失败，请刷新会话并核对业务记录，勿重复提交。"));
				} finally {
					if (heartbeat != null) heartbeat.cancel(false);
					slots.release();
					emitter.complete();
				}
			}));
		} catch (RuntimeException exception) {
			slots.release();
			throw exception;
		}
		return emitter;
	}

	@PreDestroy
	void shutdown() {
		workers.shutdownNow();
		heartbeats.shutdownNow();
	}
}
