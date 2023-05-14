package ma.enset.departementservice.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.enset.departementservice.client.FiliereClient;
import ma.enset.departementservice.constant.CoreConstants;
import ma.enset.departementservice.dto.*;
import ma.enset.departementservice.exception.DuplicateEntryException;
import ma.enset.departementservice.exception.ElementAlreadyExistsException;
import ma.enset.departementservice.exception.ElementNotFoundException;
import ma.enset.departementservice.exception.InternalErrorException;
import ma.enset.departementservice.mapper.DepartementMapper;
import ma.enset.departementservice.model.Departement;
import ma.enset.departementservice.repository.DepartementRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class DepartementServiceImpl implements DepartementService {
    private final String ELEMENT_TYPE = "Departement";
    private final String ID_FIELD_NAME = "ID";

    private final DepartementMapper departementMapper;
    private final DepartementRepository departementRepository;

    private final FiliereClient filiereClient;

    @Override
    public DepartementResponse save(final DepartementCreationRequest departementCreationRequest) throws ElementAlreadyExistsException, InternalErrorException {
        if (departementRepository.existsById(departementCreationRequest.codeDepartement())) {
            throw new ElementAlreadyExistsException(
                    CoreConstants.BusinessExceptionMessage.ALREADY_EXISTS,
                    new Object[]{ELEMENT_TYPE, ID_FIELD_NAME, departementCreationRequest.codeDepartement()},
                    null
            );
        }

        final Departement departement = departementMapper.toDepartement(departementCreationRequest);

        if (departement.getCodeChefDepartement() != null) {
            // TODO (ahmed) : check if the chef departement exists else throw exception
        }

        Departement createdDepartement = null;

        try {
            createdDepartement = departementRepository.save(departement);
        } catch (DataIntegrityViolationException e) {
            throw new ElementAlreadyExistsException(
                    CoreConstants.BusinessExceptionMessage.ALREADY_EXISTS,
                    new Object[]{ELEMENT_TYPE, ID_FIELD_NAME, departementCreationRequest.codeDepartement()},
                    null
            );
        }

        return departementMapper.toDepartementResponse(createdDepartement);
    }

    @Override
    @Transactional
    public List<DepartementResponse> saveAll(List<DepartementCreationRequest> departementCreationRequests) throws ElementAlreadyExistsException, InternalErrorException {


        final List<Departement> foundDepartements = departementRepository.findAllById(
                departementCreationRequests.stream()
                        .map(DepartementCreationRequest::codeDepartement)
                        .collect(Collectors.toSet())
        );

        if (!foundDepartements.isEmpty()) {
            throw new ElementAlreadyExistsException(
                    CoreConstants.BusinessExceptionMessage.MANY_ALREADY_EXISTS,
                    new Object[]{ELEMENT_TYPE},
                    foundDepartements.stream()
                            .map(Departement::getCodeDepartement)
                            .collect(Collectors.toList())

            );
        }

        List<String> codeChefDepartements = departementCreationRequests.stream()
                .map(DepartementCreationRequest::codeChefDepartement)
                .toList();

        // TODO (ahmed) : check if the chef departement exists else throw exception

        List<Departement> departements = departementMapper.toDepartementList(departementCreationRequests);

        List<Departement> createdDepartements;

        try {
            createdDepartements = departementRepository.saveAll(departements);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEntryException(
                    CoreConstants.BusinessExceptionMessage.DUPLICATE_ENTRY,
                    null
            );
        }

        return departementMapper.toDepartementResponseList(createdDepartements);
    }

    @Override
    public DepartementResponse findById(String id, boolean includeFilieres) throws ElementNotFoundException {
        DepartementResponse departement = departementMapper.toDepartementResponse(
                departementRepository.findById(id)
                        .orElseThrow(() ->
                                new ElementNotFoundException(
                                        CoreConstants.BusinessExceptionMessage.NOT_FOUND,
                                        new Object[]{ELEMENT_TYPE, ID_FIELD_NAME, id},
                                        null
                                ))
        );

        if (includeFilieres) {
            FiliereByDepartementResponse filiereByDepartementResponse = filiereClient.getFilieresByCodeDepartement(id, true, true, true).getBody();
            if (filiereByDepartementResponse != null)
                departement.setFilieres(filiereByDepartementResponse.filieres());
        }

        return departement;
    }

    @Override
    public List<DepartementResponse> findAllById(Set<String> ids, boolean includeFilieres) throws ElementNotFoundException {

        final List<Departement> departements = departementRepository.findAllById(
                ids
        );
        final List<String> departementIds = departements.stream()
                .map(Departement::getCodeDepartement)
                .toList();

        if (departements.size() != ids.size()) {
            throw new ElementNotFoundException(
                    CoreConstants.BusinessExceptionMessage.MANY_NOT_FOUND,
                    new Object[]{ELEMENT_TYPE},
                    ids.stream()
                            .filter(id -> !departementIds.contains(id))
                            .toList()
            );
        }

        List<DepartementResponse> departementResponses = departementMapper.toDepartementResponseList(departements);

        if (includeFilieres) {
            List<FiliereByDepartementResponse> filiereByDepartementResponse = filiereClient.getFilieresByCodesDepartement(ids, true, true, true).getBody();
            departementResponses.forEach(
                    departement -> {
                        if (filiereByDepartementResponse != null)
                            departement.setFilieres(
                                    Objects.requireNonNull(filiereByDepartementResponse.stream()
                                                    .filter(filiereByDepartementResponse1 -> filiereByDepartementResponse1.codeDepartement().equals(departement.getCodeDepartement()))
                                                    .findFirst()
                                                    .orElse(null))
                                            .filieres()
                            );
                    }
            );
        }

        return departementResponses;
    }

    @Override
    public DepartementPagingResponse findAll(final int page, final int size, final String search, boolean includeFilieres) {
        DepartementPagingResponse departementPagingResponse = departementMapper.toPagingResponse(
                departementRepository.findAllByIntituleDepartementContainsIgnoreCase(search, PageRequest.of(page, size))
        );

        if (includeFilieres) {
            List<FiliereByDepartementResponse> filiereByDepartementResponse = filiereClient.getFilieresByCodesDepartement(
                    departementPagingResponse.records()
                            .stream()
                            .map(DepartementResponse::getCodeDepartement)
                            .collect(Collectors.toSet()),
                    true,
                    true,
                    true
            ).getBody();

            departementPagingResponse.records().forEach(
                    departement -> {
                        if (filiereByDepartementResponse != null)
                            departement.setFilieres(
                                    Objects.requireNonNull(filiereByDepartementResponse.stream()
                                                    .filter(filiereByDepartementResponse1 -> filiereByDepartementResponse1.codeDepartement().equals(departement.getCodeDepartement()))
                                                    .findFirst()
                                                    .orElse(null))
                                            .filieres()
                            );
                    }
            );
        }

        return departementPagingResponse;
    }

    @Override
    public DepartementResponse update(String id, DepartementUpdateRequest departementUpdateRequest) throws ElementNotFoundException, InternalErrorException {

        final Departement departement = departementRepository.findById(id)
                .orElseThrow(() ->
                        new ElementNotFoundException(
                                CoreConstants.BusinessExceptionMessage.NOT_FOUND,
                                new Object[]{ELEMENT_TYPE, ID_FIELD_NAME, id},
                                null
                        ));

        departementMapper.updateDepartementFromDTO(departementUpdateRequest, departement);

        if (departement.getCodeChefDepartement() != null) {
            // TODO (ahmed) : check if the chef departement exists else throw exception
        }

        Departement updatedDepartement = null;

        try {
            updatedDepartement = departementRepository.save(departement);
        } catch (Exception e) {
            log.error(e.getMessage(), e.getCause());
            throw new InternalErrorException();
        }

        return departementMapper.toDepartementResponse(updatedDepartement);
    }

    @Override
    public void deleteById(String id) throws ElementNotFoundException {
        if (!departementRepository.existsById(id)) {
            throw new ElementNotFoundException(
                    CoreConstants.BusinessExceptionMessage.NOT_FOUND,
                    new Object[]{ELEMENT_TYPE, ID_FIELD_NAME, id},
                    null
            );
        }

        FiliereByDepartementResponse filiereByDepartementResponse = filiereClient.getFilieresByCodeDepartement(id, false, false, false).getBody();
        if (filiereByDepartementResponse != null) {
            Set<String> codesFiliere = filiereByDepartementResponse.filieres()
                    .stream()
                    .map(FiliereResponse::getCodeFiliere)
                    .collect(Collectors.toSet());
            filiereClient.deleteAll(codesFiliere);
        }

        departementRepository.deleteById(id);
    }

    @Override
    public void deleteById(Set<String> ids) throws ElementNotFoundException {
        this.findAllById(ids, false);

        List<FiliereByDepartementResponse> filiereByDepartementResponse = filiereClient.getFilieresByCodesDepartement(ids, false, false, false).getBody();
        if (filiereByDepartementResponse != null && !filiereByDepartementResponse.isEmpty()) {
            Set<String> codesFiliere = filiereByDepartementResponse.stream()
                    .flatMap(filiereByDepartementResponse1 -> filiereByDepartementResponse1.filieres().stream())
                    .map(FiliereResponse::getCodeFiliere)
                    .collect(Collectors.toSet());
            filiereClient.deleteAll(codesFiliere);
        }

        departementRepository.deleteAllById(ids);
    }

    @Override
    public boolean existsAllId(Set<String> codesDepartement) throws ElementNotFoundException {

        List<String> foundDepartementCodes = departementRepository.findAllById(codesDepartement)
                .stream().map(Departement::getCodeDepartement).toList();

        if (codesDepartement.size() != foundDepartementCodes.size()) {
            throw new ElementNotFoundException(
                    CoreConstants.BusinessExceptionMessage.MANY_NOT_FOUND,
                    new Object[]{ELEMENT_TYPE},
                    codesDepartement.stream()
                            .filter(code -> !foundDepartementCodes.contains(code))
                            .toList()
            );
        }

        return true;
    }

}
