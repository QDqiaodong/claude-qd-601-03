package com.equestrian.club.dto.view;

import java.math.BigDecimal;

/** 会员视图 */
public record MemberView(
        Long id,
        String memberNo,
        String name,
        String phone,
        String level,
        String levelName,
        BigDecimal cardBalance,
        Integer cardTimes,
        Boolean passedBasic,
        String passedBasicText,
        String status,
        String statusName) {
}
