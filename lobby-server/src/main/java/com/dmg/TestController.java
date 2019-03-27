package com.dmg;

import com.dmg.model.UserModel;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @PostMapping("/tt")
    public UserModel tt(UserModel userModel, String name1) {
        return userModel;
    }
}
