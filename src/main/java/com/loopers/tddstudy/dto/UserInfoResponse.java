package com.loopers.tddstudy.dto;

import com.loopers.tddstudy.domain.User;

public record UserInfoResponse (String loginId ,String name ,String birthDate,String email) {

    public  UserInfoResponse(User user) {
            this(user.getLoginId(),maskLastChar(user.getName()),user.getBirthDate(),user.getEmail());
    }

    private static String maskLastChar(String name){
        if (name == null || name.isEmpty()){
            return "*";
        }
        return  name.substring(0,name.length() -1) + "*";
    }





}



