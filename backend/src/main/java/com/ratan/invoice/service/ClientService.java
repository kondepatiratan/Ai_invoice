package com.ratan.invoice.service;

import com.ratan.invoice.dto.ClientRequest;
import com.ratan.invoice.dto.ClientResponse;
import com.ratan.invoice.entity.Client;
import com.ratan.invoice.entity.User;
import com.ratan.invoice.exception.DuplicateResourceException;
import com.ratan.invoice.exception.InvalidRequestException;
import com.ratan.invoice.exception.ResourceNotFoundException;
import com.ratan.invoice.repository.ClientRepository;
import com.ratan.invoice.repository.InvoiceRepository;
import com.ratan.invoice.repository.UserRepository;
import com.ratan.invoice.security.SecurityUtils;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final InvoiceRepository invoiceRepository;

    public ClientService(
            ClientRepository clientRepository,
            UserRepository userRepository,
            InvoiceRepository invoiceRepository) {
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional
    public ClientResponse create(ClientRequest request) {
        User owner = currentOwner();
        String email = normalizeEmail(request.email());
        if (clientRepository.existsByUserIdAndEmail(owner.getId(), email)) {
            throw new DuplicateResourceException("A client with this email already exists");
        }

        Client client = new Client();
        client.setUser(owner);
        apply(client, request, email);
        return toResponse(clientRepository.save(client));
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> list() {
        Long userId = SecurityUtils.getCurrentUser().getId();
        return clientRepository.findAllByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse get(Long id) {
        return toResponse(findOwned(id));
    }

    @Transactional
    public ClientResponse update(Long id, ClientRequest request) {
        User owner = currentOwner();
        Client client = findOwned(id, owner.getId());
        String email = normalizeEmail(request.email());
        if (clientRepository.existsByUserIdAndEmailAndIdNot(owner.getId(), email, id)) {
            throw new DuplicateResourceException("A client with this email already exists");
        }
        apply(client, request, email);
        return toResponse(client);
    }

    @Transactional
    public void delete(Long id) {
        Client client = findOwned(id);
        if (invoiceRepository.existsByClientId(client.getId())) {
            throw new InvalidRequestException("Can't delete a client that has invoices");
        }
        clientRepository.delete(client);
    }

    private Client findOwned(Long id) {
        return findOwned(id, SecurityUtils.getCurrentUser().getId());
    }

    private Client findOwned(Long id, Long userId) {
        return clientRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
    }

    private User currentOwner() {
        return userRepository.getReferenceById(SecurityUtils.getCurrentUser().getId());
    }

    private void apply(Client client, ClientRequest request, String email) {
        client.setName(request.name().trim());
        client.setEmail(email);
        client.setPhone(blankToNull(request.phone()));
        client.setGstin(blankToNull(request.gstin()));
        client.setState(blankToNull(request.state()));
        client.setAddress(blankToNull(request.address()));
    }

    private ClientResponse toResponse(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getName(),
                client.getEmail(),
                client.getPhone(),
                client.getGstin(),
                client.getState(),
                client.getAddress(),
                client.getCreatedAt());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
