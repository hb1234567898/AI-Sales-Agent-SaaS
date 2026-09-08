package com.yourcompany.salesagent.assistant.application;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.yourcompany.salesagent.assistant.api.AssistantChatRequest;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;

@Component
public class AssistantStreamTransport {
	private final AssistantChatService service;
	private final Semaphore slots = new Semaphore(8);
	private final java.util.concurrent.ExecutorService workers = Executors.newFixedThreadPool(8);
	private final java.util.concurrent.ScheduledExecutorService heartbeats = Executors.newScheduledThreadPool(1);

	public AssistantStreamTransport(AssistantChatService service) {
		this.service = service;
	}

	public SseEmitter open(AuthPrincipal principal, AssistantChatRequest request) {
		if (!slots.tryAcquire()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "助手繁忙，请稍后再试");
		try {
			var conversationId = service.beginStream(principal, request);
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
			send.accept("meta", Map.of("conversationId", conversationId));
			var heartbeat = heartbeats.scheduleAtFixedRate(
					() -> send.accept("ping", Map.of()), 15, 15, TimeUnit.SECONDS);
			try {
				workers.execute(new DelegatingSecurityContextRunnable(() -> {
					try {
						// 断开显示连接不重放或回滚已提交的业务动作，结果仍存入会话历史。
						service.streamChat(principal, conversationId, request.message(), send);
					} catch (RuntimeException exception) {
						send.accept("error", Map.of("message", "结果保存失败，请刷新会话并核对业务记录，勿重复提交。"));
					} finally {
						heartbeat.cancel(false);
						slots.release();
						emitter.complete();
					}
				}));
			} catch (RuntimeException exception) {
				heartbeat.cancel(false);
				throw exception;
			}
			return emitter;
		} catch (RuntimeException exception) {
			slots.release();
			throw exception;
		}
	}

	@PreDestroy
	void shutdown() {
		workers.shutdownNow();
		heartbeats.shutdownNow();
	}
}
