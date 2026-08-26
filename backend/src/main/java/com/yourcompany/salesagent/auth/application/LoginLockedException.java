package com.yourcompany.salesagent.auth.application;

import java.time.Instant;

public class LoginLockedException extends RuntimeException {

	private final Instant lockedUntil;

	public LoginLockedException(Instant lockedUntil) {
		super("登录尝试次数过多，请稍后再试");
		this.lockedUntil = lockedUntil;
	}

	public Instant getLockedUntil() {
		return lockedUntil;
	}
}
