package com.example.travelapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    public final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }
    
    @GetMapping("/id={id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        log.info("getUserById: {}", id);
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .body(userService.getUserById(id));
    }
    
    @PutMapping("/id={id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        log.info("updateUser, id: {}", id);
        var updated = userService.updateUser(id, user);
        return ResponseEntity.ok(updated);
    }
    
    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        log.info("create user");
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(userService.createUser(user));
    }
    
    @DeleteMapping("/id={id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        log.info("delete user, id: {}", id);
        return ResponseEntity.ok().build();
    }
}
