package com.example.userservice;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    //private final ConcurrentHashMap<Long, User> userMap = new ConcurrentHashMap<>();
    
    public UserService(UserRepository userRepository, UserSettingsRepository userSettingsRepository) {
        this.userRepository = userRepository;
        this.userSettingsRepository = userSettingsRepository;
    }
    
    public UserEntity getUserById(Long id) {
        return userRepository.findById(id).orElseThrow(
                ()->new UserNotFoundException(id)
        );
    }
    
    @Transactional
    public UserEntity createUser(UserEntity user) {
        var settingsEntity = new UserSettingsEntity(
                null, 
                user, 
                null,
                "WHITE", 
                "ru", 
                null
        );
        userRepository.save(user);
        userSettingsRepository.save(settingsEntity);
        return user;
    }
    
    public UserSettingsEntity updateUserSettings(Long userId, UserSettingsEntity newUserSettings) {
        UserSettingsEntity settingsEntity = userSettingsRepository.findByUserId(userId).orElseThrow(
                ()->new UserNotFoundException(userId)
        );
        var updatedEntity = new UserSettingsEntity(
                settingsEntity.getId(), 
                settingsEntity.getUser(), 
                newUserSettings.getTelegramId(), 
                newUserSettings.getTheme(), 
                newUserSettings.getLanguage(), 
                newUserSettings.getNotificationMethod()
        );
        return userSettingsRepository.save(updatedEntity);
    }
    
    public UserSettingsEntity bindTelegram(Long userId, Long telegramId) {
        UserSettingsEntity settings = getUserSettingsByUserId(userId);
        return updateUserSettings(userId, new UserSettingsEntity(
                null, 
                null, 
                telegramId, 
                settings.getTheme(), 
                settings.getLanguage(), 
                settings.getNotificationMethod()
        ));
    }
    
    public UserEntity updateUser(Long id, UserEntity user) {
        UserEntity userEntity = userRepository
                .findById(id)
                .orElseThrow(
                        ()->new UserNotFoundException(id)
                );
        userEntity.setTimezone(user.getTimezone());
        userEntity.setCurrency(user.getCurrency());
        userEntity.setAmount(user.getAmount());
        userEntity.setName(user.getName());
        return userRepository.save(userEntity);
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException(id);
        }
        userSettingsRepository.deleteByUserId(id);
        userRepository.deleteById(id);
    }
    
    public UserEntity getUserByTelegramId(Long telegramId) {
        return userSettingsRepository
                .findByTelegramId(telegramId)
                .orElseThrow(()->new UserSettingsNotFoundException(telegramId))
                .getUser();
    }
    
    public Long getTelegramIdByUserId(Long userId) {
        return getUserSettingsByUserId(userId).getTelegramId();
    }
    
    public UserSettingsEntity getUserSettingsByUserId(Long userId) {
        return userSettingsRepository.findByUserId(userId).orElseThrow(
                ()->new UserSettingsNotFoundException(userId)
        );
    }
    
}
