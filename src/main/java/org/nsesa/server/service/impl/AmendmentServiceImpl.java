package org.nsesa.server.service.impl;

import com.inspiresoftware.lib.dto.geda.adapter.ValueConverter;
import com.inspiresoftware.lib.dto.geda.assembler.Assembler;
import com.inspiresoftware.lib.dto.geda.assembler.DTOAssembler;
import com.inspiresoftware.lib.dto.geda.assembler.dsl.impl.DefaultDSLRegistry;
import org.apache.cxf.annotations.GZIP;
import org.nsesa.server.domain.*;
import org.nsesa.server.dto.AmendmentContainerDTO;
import org.nsesa.server.dto.PersonDTO;
import org.nsesa.server.dto.RevisionDTO;
import org.nsesa.server.exception.ResourceNotFoundException;
import org.nsesa.server.exception.StaleResourceException;
import org.nsesa.server.exception.ValidationException;
import org.nsesa.server.repository.AmendmentContainerRepository;
import org.nsesa.server.repository.DocumentRepository;
import org.nsesa.server.repository.GroupRepository;
import org.nsesa.server.repository.PersonRepository;
import org.nsesa.server.service.api.AmendmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.*;

/**
 * Date: 11/03/13 15:52
 *
 * @author <a href="mailto:philip.luppens@gmail.com">Philip Luppens</a>
 * @version $Id$
 */
@WebService(endpointInterface = "org.nsesa.server.service.api.AmendmentService", serviceName = "AmendmentService")
@GZIP
@Path("/amendment")
public class AmendmentServiceImpl implements AmendmentService {

    @Autowired
    DocumentRepository documentRepository;

    @Autowired
    PersonRepository personRepository;

    @Autowired
    AmendmentContainerRepository amendmentContainerRepository;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    ValueConverter documentIDConvertor;

    @Autowired
    ValueConverter personIDConvertor;

    @Autowired
    ValueConverter amendableWidgetReferenceConvertor;

    @Autowired
    ValueConverter amendmentActionConvertor;

    @Autowired
    ValueConverter bundledAmendmentContainerConvertor;

    @Autowired
    ValueConverter groupConvertor;

    @Autowired
    ValueConverter membershipToGroupConvertor;

    private final Assembler amendmentContainerAssembler = DTOAssembler.newAssembler(AmendmentContainerDTO.class, AmendmentContainer.class);
    private final Assembler personAssembler = DTOAssembler.newAssembler(PersonDTO.class, Person.class);

    @GET
    @Path("/document/{documentID}/{personID}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    @Transactional(readOnly = true)
    @Override
    public List<AmendmentContainerDTO> getAmendmentContainersByDocumentAndPerson(@PathParam("documentID") String documentID, @PathParam("personID") String personID) throws ResourceNotFoundException {
        final Person person = personRepository.findByPersonID(personID);
        if (person != null) {
            final Document document = documentRepository.findByDocumentID(documentID);
            if (document != null) {
                Set<AmendmentContainer> collapsed = new HashSet<AmendmentContainer>();
                // finds all that were made by the the current user
                final List<AmendmentContainer> amendmentContainers = amendmentContainerRepository.findByDocumentAndPersonAndLatestRevision(document, person, true);
                collapsed.addAll(amendmentContainers);
                for (final Membership membership : person.getMemberships()) {
                    final List<AmendmentContainer> amendmentContainersByGroup = amendmentContainerRepository.findByDocumentAndPersonAndLatestRevision(document, membership.getGroup(), true);
                    collapsed.addAll(amendmentContainersByGroup);
                }
                final List<AmendmentContainerDTO> amendmentContainerDTOs = new ArrayList<AmendmentContainerDTO>();
                amendmentContainerAssembler.assembleDtos(amendmentContainerDTOs, collapsed, getConvertors(), new DefaultDSLRegistry());
                return amendmentContainerDTOs;
            }
            throw new ResourceNotFoundException("No document with documentID " + documentID + " found.");
        }
        throw new ResourceNotFoundException("No person with personID " + personID + " found.");
    }

    @GET
    @Path("/id/{amendmentContainerID}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    @Transactional(readOnly = true)
    @Override
    public AmendmentContainerDTO getAmendmentContainer(@PathParam("amendmentContainerID") String amendmentContainerID) throws ResourceNotFoundException {
        final AmendmentContainerDTO amendmentContainerDTO = new AmendmentContainerDTO();
        final AmendmentContainer amendmentContainer = amendmentContainerRepository.findByAmendmentContainerIDAndLatestRevision(amendmentContainerID, true);
        if (amendmentContainer != null) {
            amendmentContainerAssembler.assembleDto(amendmentContainerDTO, amendmentContainer, getConvertors(), new DefaultDSLRegistry());
            return amendmentContainerDTO;
        }
        throw new ResourceNotFoundException("No amendment with amendmentContainerID " + amendmentContainerID + " found.");
    }

    @GET
    @Path("/document/{documentID}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    @Transactional(readOnly = true)
    @Override
    public List<AmendmentContainerDTO> getAmendmentContainersByDocument(@PathParam("documentID") String documentID) throws ResourceNotFoundException {
        final Document document = documentRepository.findByDocumentID(documentID);
        if (document != null) {
            final List<AmendmentContainer> amendmentContainers = amendmentContainerRepository.findByDocument(document);
            final List<AmendmentContainerDTO> amendmentContainerDTOs = new ArrayList<AmendmentContainerDTO>();
            amendmentContainerAssembler.assembleDtos(amendmentContainerDTOs, amendmentContainers, getConvertors(), new DefaultDSLRegistry());
            return amendmentContainerDTOs;
        }
        throw new ResourceNotFoundException("No document with documentID " + documentID + " found.");
    }

    @GET
    @Path("/revision/{revisionID}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    @Transactional(readOnly = true)
    @Override
    public AmendmentContainerDTO getAmendmentContainerVersion(@PathParam("revisionID") String revisionID) throws ResourceNotFoundException {
        AmendmentContainerDTO amendmentContainerDTO = new AmendmentContainerDTO();
        AmendmentContainer amendmentContainer = amendmentContainerRepository.findByRevisionID(revisionID);
        if (amendmentContainer != null) {
            amendmentContainerAssembler.assembleDto(amendmentContainerDTO, amendmentContainer, getConvertors(), new DefaultDSLRegistry());
            return amendmentContainerDTO;
        }
        throw new ResourceNotFoundException("No amendment with revision " + revisionID + " found.");
    }

    @POST
    @Path("/share/{amendmentContainerID}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    @Transactional()
    @Override
    public AmendmentContainerDTO share(@PathParam("amendmentContainerID") String amendmentContainerID, @QueryParam("groupID") String groupID, @QueryParam("share") Boolean share) throws ResourceNotFoundException {

        final AmendmentContainerDTO amendmentContainerDTO = new AmendmentContainerDTO();
        final AmendmentContainer amendmentContainer = amendmentContainerRepository.findByAmendmentContainerIDAndLatestRevision(amendmentContainerID, true);
        if (amendmentContainer != null) {

            if (share) {
                Group group = groupRepository.findByGroupID(groupID);
                if (group != null) {
                    amendmentContainer.getGroups().add(group);
                }
            }
            else {
                Iterator<Group> iterator = amendmentContainer.getGroups().iterator();
                while (iterator.hasNext()) {
                    Group group = iterator.next();
                    if (group.getGroupID().equals(groupID)) {
                        iterator.remove();
                    }
                }
            }
            amendmentContainerRepository.save(amendmentContainer);
            amendmentContainerAssembler.assembleDto(amendmentContainerDTO, amendmentContainer, getConvertors(), new DefaultDSLRegistry());
            return amendmentContainerDTO;
        }
        throw new ResourceNotFoundException("No amendment with amendmentContainerID " + amendmentContainerID + " found.");
    }

    @GET
    @Path("/revisions/{amendmentContainerID}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    @Transactional(readOnly = true)
    @Override
    public List<RevisionDTO> getAmendmentContainerVersions(@PathParam("amendmentContainerID") String amendmentContainerID) {
        final List<AmendmentContainer> amendmentContainers = amendmentContainerRepository.findByAmendmentContainerIDOrderByCreationDateDesc(amendmentContainerID);
        final List<RevisionDTO> revisionIDs = new ArrayList<RevisionDTO>();
        for (final AmendmentContainer amendmentContainer : amendmentContainers) {
            PersonDTO person = new PersonDTO();
            personAssembler.assembleDto(person, amendmentContainer.getPerson(), getConvertors(), new DefaultDSLRegistry());
            RevisionDTO revision = new RevisionDTO(
                    person,
                    new Date(amendmentContainer.getCreationDate().getTime().getTime()),
                    amendmentContainer.getModificationDate() != null ? new Date(amendmentContainer.getModificationDate().getTime().getTime()) : null,
                    amendmentContainer.getRevisionID()
            );
            revisionIDs.add(revision);
        }
        return revisionIDs;
    }

    @POST
    @Path("/save")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    @Transactional()
    @Override
    public AmendmentContainerDTO save(final AmendmentContainerDTO amendmentContainerDTO) throws StaleResourceException, ResourceNotFoundException, ValidationException {
        // see if we already have an existing amendment under this revision
        final AmendmentContainer previous = amendmentContainerRepository.findByAmendmentContainerIDAndLatestRevision(amendmentContainerDTO.getAmendmentContainerID(), true);
        AmendmentContainer amendmentContainer = new AmendmentContainer();
        // force the source reference to be new
        amendmentContainerDTO.getSourceReference().setReferenceID(UUID.randomUUID().toString());
        for (AmendableWidgetReference targetReference : amendmentContainer.getTargetReferences()) {
            targetReference.setReferenceID(UUID.randomUUID().toString());
        }

        amendmentContainerAssembler.assembleEntity(amendmentContainerDTO, amendmentContainer, getConvertors(), new DefaultDSLRegistry());

        if (previous != null) {
            if (amendmentContainerDTO.getRevisionID().equals(previous.getRevisionID())) {
                // set new revision
                amendmentContainer.setRevisionID(UUID.randomUUID().toString());
                amendmentContainer.setPreviousAmendmentContainer(previous);
                // pass the 'latest' flag from the previous to the new one
                previous.setLatestRevision(false);

            } else {
                throw new StaleResourceException("Stale resource detected; given revisionID is " + amendmentContainerDTO.getRevisionID() + ", latest revision in database is " + previous.getRevisionID());
            }
        }
        amendmentContainer.setLatestRevision(true);

        // make sure to set the creation date if the amendment is new (as well as the modification date)
        // TODO use @BeforePersist callbacks
        final Calendar calendar = GregorianCalendar.getInstance();
        if (amendmentContainer.getCreationDate() == null) amendmentContainer.setCreationDate(calendar);
        amendmentContainer.setModificationDate(calendar);

        amendmentContainer = amendmentContainerRepository.save(amendmentContainer);
        amendmentContainerAssembler.assembleDto(amendmentContainerDTO, amendmentContainer, getConvertors(), new DefaultDSLRegistry());
        return amendmentContainerDTO;
    }

    @DELETE
    @Path("/id/{amendmentContainerID}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    @Transactional()
    @Override
    public void delete(final @PathParam("amendmentContainerID") String amendmentContainerID) {
        final List<AmendmentContainer> amendmentContainers = amendmentContainerRepository.findByAmendmentContainerIDOrderByCreationDateDesc(amendmentContainerID);
        if (amendmentContainers != null) {
            for (final AmendmentContainer amendmentContainer : amendmentContainers) {
                if (amendmentContainer != null) {
                    amendmentContainerRepository.delete(amendmentContainer);
                }
            }
        }
    }

    @POST
    @Path("/status/{revisionID}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    @Transactional()
    @Override
    public String updateStatus(@PathParam("revisionID") String revisionID, @QueryParam("status") String newStatus) throws StaleResourceException, ResourceNotFoundException {
        final AmendmentContainer amendmentContainer = amendmentContainerRepository.findByRevisionID(revisionID);
        if (amendmentContainer != null) {
            if (newStatus != null) {
                if (amendmentContainer.isLatestRevision()) {
                    amendmentContainer.setAmendmentContainerStatus(newStatus);
                    amendmentContainerRepository.save(amendmentContainer);
                } else {
                    throw new StaleResourceException("Stale resource detected; given revisionID is " + revisionID + ", latest revision in database is " + amendmentContainer.getRevisionID());
                }
            }
            return amendmentContainer.getAmendmentContainerStatus();
        }
        throw new ResourceNotFoundException("No amendment with revision " + revisionID + " found.");
    }

    private Map<String, Object> getConvertors() {
        return new HashMap<String, Object>() {
            {
                put("documentIDConvertor", documentIDConvertor);
                put("personIDConvertor", personIDConvertor);
                put("groupConvertor", groupConvertor);
                put("membershipToGroupConvertor", membershipToGroupConvertor);
                put("amendableWidgetReferenceConvertor", amendableWidgetReferenceConvertor);
                put("amendmentActionConvertor", amendmentActionConvertor);
                put("bundledAmendmentContainerConvertor", bundledAmendmentContainerConvertor);
            }
        };
    }
}
