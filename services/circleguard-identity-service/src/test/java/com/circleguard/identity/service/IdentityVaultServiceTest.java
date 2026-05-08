package com.circleguard.identity.service;

import com.circleguard.identity.model.IdentityMapping;
import com.circleguard.identity.repository.IdentityMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdentityVaultServiceTest {

    @Mock
    private IdentityMappingRepository repository;

    @InjectMocks
    private IdentityVaultService identityVaultService;

    @BeforeEach
    void setUp() throws Exception {
        // Inyectamos el valor que normalmente pone Spring Boot con @Value
        Field saltField = IdentityVaultService.class.getDeclaredField("hashSalt");
        saltField.setAccessible(true);
        saltField.set(identityVaultService, "secret-test-salt-123");
    }

    @Test
    void test1_getOrCreateAnonymousId_NewIdentity_SavesAndReturnsId() {
        String realIdentity = "user@example.com";
        UUID newUuid = UUID.randomUUID();
        
        IdentityMapping newMapping = IdentityMapping.builder()
                .anonymousId(newUuid)
                .realIdentity(realIdentity)
                .build();

        // Simulamos que NO existe en la base de datos
        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.empty());
        // Simulamos que al guardarse, se le auto-asigna un UUID
        when(repository.save(any(IdentityMapping.class))).thenReturn(newMapping);

        UUID result = identityVaultService.getOrCreateAnonymousId(realIdentity);

        assertNotNull(result);
        assertEquals(newUuid, result);
        verify(repository, times(1)).save(any(IdentityMapping.class));
    }

    @Test
    void test2_getOrCreateAnonymousId_ExistingIdentity_ReturnsExistingId() {
        String realIdentity = "existingUser@example.com";
        UUID existingUuid = UUID.randomUUID();
        
        IdentityMapping existingMapping = IdentityMapping.builder()
                .anonymousId(existingUuid)
                .realIdentity(realIdentity)
                .build();

        // Simulamos que YA existe en la base de datos
        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.of(existingMapping));

        UUID result = identityVaultService.getOrCreateAnonymousId(realIdentity);

        assertNotNull(result);
        assertEquals(existingUuid, result);
        // Validamos que NUNCA intentó guardar un registro nuevo duplicado
        verify(repository, never()).save(any(IdentityMapping.class));
    }

    @Test
    void test3_resolveRealIdentity_ExistingMap_ReturnsRealIdentity() {
        UUID targetId = UUID.randomUUID();
        String expectedIdentity = "admin@hospital.com";
        
        IdentityMapping mapping = IdentityMapping.builder()
                .anonymousId(targetId)
                .realIdentity(expectedIdentity)
                .build();

        when(repository.findById(targetId)).thenReturn(Optional.of(mapping));

        String result = identityVaultService.resolveRealIdentity(targetId);

        assertEquals(expectedIdentity, result);
    }

    @Test
    void test4_resolveRealIdentity_NotFound_ThrowsException() {
        UUID randomId = UUID.randomUUID();
        
        // Simulamos un ID que no está registrado
        when(repository.findById(randomId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> {
            identityVaultService.resolveRealIdentity(randomId);
        }, "Debe lanzar un 404 NOT FOUND si la identidad no existe");
    }
}
