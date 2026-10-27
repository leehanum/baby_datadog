package com.lee.baby_datadog.member.repository;

import com.lee.baby_datadog.member.domain.Member;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MemberRepository {

    private final EntityManager em;

    public void create(Member member){
        em.persist(member);
    }

    public Member findMemberById(Long id){
        return em.find(Member.class, id);
    }

    public Optional<Member> findMemberByEmail(String email){
        TypedQuery<Member> typedQuery = em.createQuery(
                "select m from Member m where m.email = :email", Member.class);

        typedQuery.setParameter("email", email);

        try {
            Member member = typedQuery.getSingleResult();
            return Optional.ofNullable(member);
        } catch (NoResultException e){
            return Optional.empty();
        }
    }

    public List<Member> findAll(){
        return em.createQuery("select m from Member m", Member.class)
                .getResultList();
    }

    public List<Member> findByName(String name){
        return em.createQuery("select m from Member m where m.name = :name", Member.class)
                .setParameter("name", name)
                .getResultList();
    }
}
