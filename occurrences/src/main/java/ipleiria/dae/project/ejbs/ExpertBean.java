package ipleiria.dae.project.ejbs;

import ipleiria.dae.project.entities.Client;
import ipleiria.dae.project.entities.Expert;
import ipleiria.dae.project.entities.Occurrence;
import ipleiria.dae.project.enumerators.State;
import ipleiria.dae.project.exceptions.MyEntityExistsException;
import ipleiria.dae.project.exceptions.MyEntityNotFoundException;
import ipleiria.dae.project.exceptions.NotAuthorizedException;
import ipleiria.dae.project.security.Hasher;
import org.hibernate.Hibernate;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceContext;
import java.util.List;

@Stateless
public class ExpertBean {
    @PersistenceContext
    EntityManager em;
    @EJB
    private EmailBean emailBean;
    @Inject
    private Hasher hasher;

    public List<Expert> getAllExperts() {
        try {
            return (List<Expert>) em.createNamedQuery("getAllExperts").getResultList();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("No experts found");
        }
    }

    public Expert create(String username, String password, String name, String email, String insuranceCompany) throws MyEntityExistsException, IllegalArgumentException{
        // Find Insurance Company
        String company = findInsuranceCompany(insuranceCompany);

        // Verify if the username already exists
        Expert expert = find(username);
        validateExpertDoesNotExist(expert);

        // Create Expert
        Expert newExpert = new Expert(username, hasher.hash(password), name, email, company);
        em.persist(newExpert);
        return newExpert;
    }

    public Expert update(String username, String name, String email, String insuranceCompany) throws MyEntityNotFoundException {
        try {
            // Find company
            String company = MockAPIBean.getInsuranceCompany(insuranceCompany);
            validateCompanyExists(company);

            // Find Expert
            Expert expert = em.find(Expert.class, username);
            validateExpertExists(expert);

            // Update
            expert.setName(name);
            expert.setEmail(email);
            expert.setInsuranceCompany(company);

            // To ensure the changes are persisted to the database
            em.merge(expert);

            return expert;
        } catch (MyEntityNotFoundException e) {
            throw new MyEntityNotFoundException(e.getMessage());
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public Expert find(String username) throws MyEntityNotFoundException {
        return em.find(Expert.class, username);
    }

    public Expert findOrFail(String username) throws MyEntityNotFoundException {
        Expert expert = em.find(Expert.class, username);
        validateExpertExists(expert);
        return expert;
    }

    public String findInsuranceCompany(String insuranceCompany) throws MyEntityNotFoundException {
        // Find Insurance Company
        String company = MockAPIBean.getInsuranceCompany(insuranceCompany);
        validateCompanyExists(company);
        return company;
    }

    public void delete(String username) throws MyEntityNotFoundException {
        // Find Expert
        Expert expert = find(username);
        validateExpertExists(expert);

        // Delete Expert
        em.remove(expert);
    }

    public void disapproveOccurrence(String username, long occurrenceCode, String description) throws MyEntityNotFoundException, NotAuthorizedException {
        // Find Expert
        Expert expert = find(username);
        validateExpertExists(expert);

        // Find Occurrence
        Occurrence occurrence = em.find(Occurrence.class, occurrenceCode);
        validateOccurrence(expert, occurrence);

        validateOccurrenceState(occurrence, State.PENDING);

        // Disapprove Occurrence
        occurrence.setState(State.DISAPPROVED);

        // Get Occurrence Description
        String occurrenceDescription = occurrence.getDescription();

        // Build Occurrence Description
        String newOccurrenceDescription = occurrenceDescription + "\n[" + expert.getUsername() + "]: " + description;
        occurrence.setDescription(newOccurrenceDescription);

        // Send Email to Client
        sendDisapprovalEmail(occurrence, expert, newOccurrenceDescription);
    }

    private void sendDisapprovalEmail(Occurrence occurrence, Expert expert, String newOccurrenceDescription) {
        emailBean.send(
                occurrence.getClient().getEmail(),
                "Occurrence " + occurrence.getId() + " disapproved",
                "Your occurrence was disapproved by " + expert.getUsername() + ".\n\n" + newOccurrenceDescription
        );
    }

    public void approveOccurrence(String username, long occurrenceCode, String description) throws MyEntityNotFoundException, NotAuthorizedException {
        try {
            // Find Expert
            Expert expert = find(username);
            validateExpertExists(expert);

            // Find Occurrence
            Occurrence occurrence = em.find(Occurrence.class, occurrenceCode);
            validateOccurrence(expert, occurrence);

            validateOccurrenceState(occurrence, State.PENDING);

            // Approve Occurrence
            occurrence.setState(State.APPROVED);

            // Get Occurrence Description
            String occurrenceDescription = occurrence.getDescription();

            // Build Occurrence Description
            String newOccurrenceDescription = occurrenceDescription + "\n[" + expert.getUsername() + "]: " + description;
            occurrence.setDescription(newOccurrenceDescription);

            // Send Email to Client
            sendApprovalEmail(occurrence, expert, newOccurrenceDescription);

        } catch (MyEntityNotFoundException e) {
            throw new MyEntityNotFoundException(e.getMessage());
        } catch (NotAuthorizedException e) {
            throw new NotAuthorizedException(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private void sendApprovalEmail(Occurrence occurrence, Expert expert, String newOccurrenceDescription) {
        emailBean.send(
                occurrence.getClient().getEmail(),
                "Occurrence " + occurrence.getId() + " approved",
                "Your occurrence was approved by " + expert.getUsername() + ".\n\n" + newOccurrenceDescription
        );
    }

    public void addOccurrence(String username, long occurrenceCode) throws MyEntityNotFoundException, NotAuthorizedException {
        try {
            // Find Expert
            Expert expert = find(username);
            validateExpertExists(expert);

            // Find Occurrence
            Occurrence occurrence = em.find(Occurrence.class, occurrenceCode);

            if (!expert.getInsuranceCompany().equals(occurrence.getInsurance().getInsuranceCompany())) {
                throw new IllegalArgumentException("Expert and Occurrence are not from the same company");
            }

            // Validate Occurrence
            validateOccurrenceExists(occurrence);

            // Validate if the occurrence is PENDING
            validateOccurrenceState(occurrence, State.PENDING);

            expert.addOccurrence(occurrence);
            occurrence.addExpert(expert);
        } catch (MyEntityNotFoundException e) {
            throw new MyEntityNotFoundException(e.getMessage());
        } catch (NotAuthorizedException e) {
            throw new NotAuthorizedException(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public void removeOccurrence(String username, long occurrenceCode) throws MyEntityNotFoundException, NotAuthorizedException {
        try {
            // Find Expert
            Expert expert = find(username);
            validateExpertExists(expert);

            // Find Occurrence
            Occurrence occurrence = em.find(Occurrence.class, occurrenceCode);
            validateOccurrence(expert, occurrence);

            expert.removeOccurrence(occurrence);
            occurrence.removeExpert(expert);
        } catch (MyEntityNotFoundException e) {
            throw new MyEntityNotFoundException(e.getMessage());
        } catch (NotAuthorizedException e) {
            throw new NotAuthorizedException(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public List<Occurrence> occurrences(String username) throws MyEntityNotFoundException {
        try {
            // Find Expert
            Expert expert = find(username);
            validateExpertExists(expert);

            // Get Occurrences
            Hibernate.initialize(expert.getOccurrences());
            return expert.getOccurrences();
        } catch (MyEntityNotFoundException e) {
            throw new MyEntityNotFoundException(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public void acceptRepairer(String username, long occurrenceCode) throws MyEntityNotFoundException, NotAuthorizedException {
        try {
            // Find Expert
            Expert expert = find(username);
            validateExpertExists(expert);

            // Find Occurrence
            Occurrence occurrence = em.find(Occurrence.class, occurrenceCode);
            validateOccurrence(expert, occurrence);

            validateExpertIsAssignedToOccurrence(expert, occurrence);

            validateOccurrenceState(occurrence, State.WAITING_FOR_APPROVAL_OF_REPAIRER_BY_EXPERT);

            occurrence.setState(State.REPAIRER_WAITING_LIST);

            // Send Email to Repairer about being accepted to repair the occurrence
            emailBean.send(occurrence.getRepairer().getEmail(), "Occurrence " + occurrence.getId() + " accepted",
                    "You were accepted to repair the occurrence " + occurrence.getId() + " by " + expert.getUsername() + ".\n\n" + occurrence.getDescription());

            // Send Email to Client about the Repairer of the occurrence being accepted by the Expert
            emailBean.send(occurrence.getClient().getEmail(), "Occurrence " + occurrence.getId() + " accepted",
                    "The repairer " + occurrence.getRepairer().getUsername() + " was accepted to repair the occurrence " + occurrence.getId() + " by " + expert.getUsername() + ".\n\n" + occurrence.getDescription());

        } catch (MyEntityNotFoundException e) {
            throw new MyEntityNotFoundException(e.getMessage());
        } catch (NotAuthorizedException e) {
            throw new NotAuthorizedException(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public void rejectRepairer(String username, long occurrenceCode, String description) throws MyEntityNotFoundException, NotAuthorizedException {
        try {
            // Find Expert
            Expert expert = find(username);
            validateExpertExists(expert);

            // Find Occurrence
            Occurrence occurrence = em.find(Occurrence.class, occurrenceCode);
            validateOccurrence(expert, occurrence);

            validateExpertIsAssignedToOccurrence(expert, occurrence);

            validateOccurrenceState(occurrence, State.WAITING_FOR_APPROVAL_OF_REPAIRER_BY_EXPERT);

            //Reject current assigned repairer
            occurrence.setRepairer(null);

            occurrence.setState(State.APPROVED);

            // Get Occurrence Description
            String occurrenceDescription = occurrence.getDescription();

            // Build Occurrence Description
            String newOccurrenceDescription = occurrenceDescription + "\n[" + expert.getUsername() + "]: " + description;
            occurrence.setDescription(newOccurrenceDescription);
        } catch (MyEntityNotFoundException e) {
            throw new MyEntityNotFoundException(e.getMessage());
        } catch (NotAuthorizedException e) {
            throw new NotAuthorizedException(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private void validateExpertExists(Expert expert) throws MyEntityNotFoundException {
        if (expert == null) {
            throw new MyEntityNotFoundException("Expert not found");
        }
    }

    private void validateExpertDoesNotExist(Expert expert) throws MyEntityExistsException {
        if (expert != null) {
            throw new MyEntityExistsException("Expert " + expert.getUsername() + " already exists");
        }
    }

    private void validateOccurrenceExists(Occurrence occurrence) throws MyEntityNotFoundException {
        if (occurrence == null) {
            throw new MyEntityNotFoundException("Occurrence not found");
        }
    }

    public void validateCompanyExists(String company) throws MyEntityNotFoundException {
        if (company.equals("")) {
            throw new MyEntityNotFoundException("Company not found");
        }
    }

    private void validateOccurrence(Expert expert, Occurrence occurrence) throws MyEntityNotFoundException, NotAuthorizedException {
        try {
            validateOccurrenceExists(occurrence);
            validateExpertIsAssignedToOccurrence(expert, occurrence);
            validateOccurrenceState(occurrence, State.PENDING);
        } catch (MyEntityNotFoundException e) {
            throw new MyEntityNotFoundException(e.getMessage());
        } catch (NotAuthorizedException e) {
            throw new NotAuthorizedException(e.getMessage());
        }
    }

    private void validateExpertIsAssignedToOccurrence(Expert expert, Occurrence occurrence) throws NotAuthorizedException {
        // Check if Expert is assigned to Occurrence
        if (!occurrence.isExpertInOccurrence(expert)) {
            throw new NotAuthorizedException("Expert " + expert.getUsername() + " is not assigned to this occurrence " + occurrence.getId());
        }
    }

    private void validateOccurrenceState(Occurrence occurrence, State state) throws NotAuthorizedException {
        // Check if Occurrence is in the correct state
        if (occurrence.getState() != state) {
            throw new NotAuthorizedException("Occurrence is not in the correct state, current state is " + occurrence.getState());
        }
    }

    public Expert updatePassword(String username, String password) {
        Expert expert = find(username);
        if (expert == null) {
            throw new MyEntityNotFoundException("Expert not found");
        }
        em.lock(expert, LockModeType.OPTIMISTIC);
        expert.setPassword(hasher.hash(password));
        return expert;
    }
}
