package com.hookflow.common;

import java.util.List;

import org.springframework.data.domain.Page;

/** One page of a list endpoint's results. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems) {

	public static <T> PageResponse<T> of(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
	}

}
