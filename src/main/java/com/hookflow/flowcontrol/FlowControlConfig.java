package com.hookflow.flowcontrol;

import com.hookflow.config.HookflowProperties;
import com.hookflow.config.HookflowProperties.FlowControl;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Picks the flow controller named by {@code hookflow.flow-control.mode}. */
@Configuration
class FlowControlConfig {

	@Bean
	FlowController flowController(HookflowProperties properties, StringRedisTemplate redis, MeterRegistry meterRegistry) {
		FlowControl config = properties.flowControl();
		return switch (config.mode()) {
			case NONE -> new NoFlowController();
			case FIXED, ADAPTIVE -> new RedisFlowController(redis, config, meterRegistry);
		};
	}

}
