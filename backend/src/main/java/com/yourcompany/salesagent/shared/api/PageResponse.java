package com.yourcompany.salesagent.shared.api;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

public record PageResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean first,
		boolean last) {

	public static <T> PageResponse<T> from(IPage<T> page) {
		return new PageResponse<>(
				page.getRecords(),
				(int) page.getCurrent() - 1,
				(int) page.getSize(),
				page.getTotal(),
				(int) page.getPages(),
				page.getCurrent() <= 1,
				page.getCurrent() >= page.getPages());
	}
}
