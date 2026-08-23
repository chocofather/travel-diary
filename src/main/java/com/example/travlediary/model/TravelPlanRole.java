package com.example.travlediary.model;

/**
 * travel_plan_members.role. DB 는 varchar 이고 enum 이름을 그대로 저장한다.
 * 방을 만든 사람이 OWNER, 초대로 들어온 사람이 MEMBER 다.
 */
public enum TravelPlanRole {
    OWNER, MEMBER
}
