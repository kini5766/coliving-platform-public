package com.coliving.platform.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled를 켜는 스위치. 이게 없으면 @Scheduled를 붙여도 아무 일도 일어나지 않는다.
 * (ContractApplication에 붙여도 되지만, 설정은 config 폴더에 모아 둔다)
 *
 * @author 한성현
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
