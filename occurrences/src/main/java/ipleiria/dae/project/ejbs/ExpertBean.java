package ipleiria.dae.project.ejbs;

import ipleiria.dae.project.entities.Company;
import ipleiria.dae.project.entities.Expert;
import ipleiria.dae.project.security.Hasher;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

@Stateless
public class ExpertBean {
    @PersistenceContext
    EntityManager em;

    @Inject // import javax.inject.Inject;
    private Hasher hasher;

    public List<Expert> getAllExperts() {
        return (List<Expert>) em.createNamedQuery("getAllExperts").getResultList();
    }

    public Expert create(String username, String password, String name, String email, String company_username) {
        Expert expert = find(username);
        Company company = em.find(Company.class, company_username);

        if(company == null ){
            return null;
        }

        if (expert != null){
            return null;
        }
        expert = new Expert(username, hasher.hash(password), name, email, company);
        em.persist(expert);
        company.addExpert(expert);
        return find(username);
    }

    public Expert update(String username, String password, String name, String email, long company_usernmae) {
        Expert expert = find(username);
        if (expert == null){
            return null;
        }
        Company company = em.find(Company.class, company_usernmae);

        if(company == null ){
            return null;
        }
        expert.setPassword(password);
        expert.setName(name);
        expert.setEmail(email);
        expert.setCompany(company);
        return expert;
    }

    public Expert find(String username) {
        return em.find(Expert.class, username);
    }

    public void delete(String username) {
        Expert expert = find(username);
        if (expert == null){

        }

        Company company = expert.getCompany();
        company.removeExpert(expert);
        em.remove(expert);
    }

}