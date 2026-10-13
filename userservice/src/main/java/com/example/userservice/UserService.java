package com.example.userservice;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository userRepository;
    //private final ConcurrentHashMap<Long, User> userMap = new ConcurrentHashMap<>();
    
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    
    public User getUserById(Long id) {
        return toDomainUser(userRepository.findById(id).orElseThrow(
                ()->new UserNotFoundException(id)
        ));
    }
    
    public User createUser(User user) {
        var entityToSave = new UserEntity(
                null, 
                user.timezone(), 
                user.currency(), 
                user.amount(), 
                user.name()
        );
        assert userRepository != null;
        userRepository.save(entityToSave);
        return toDomainUser(entityToSave);
    }
    
    public User updateUser(Long id, User user) {
        UserEntity userEntity = userRepository
                .findById(id)
                .orElseThrow(
                        ()->new UserNotFoundException(id)
                );
        var updatedEntity = new UserEntity(userEntity.getId(), user.timezone(), user.currency(), user.amount(), user.name());
        
        var updated = userRepository.save(updatedEntity);
        return toDomainUser(updated);
    }
    
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
    
    private User toDomainUser(UserEntity userEntity) {
        return new User(
                userEntity.getId(), 
                userEntity.getTimezone(), 
                userEntity.getCurrency(), 
                userEntity.getAmount(), 
                userEntity.getName()
        );
    }
}
