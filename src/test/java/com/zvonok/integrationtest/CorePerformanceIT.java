package com.zvonok.integrationtest;

import com.zvonok.model.*;
import com.zvonok.repository.*;
import com.zvonok.service.*;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Separate opt-in benchmark: mvn -Dtest=CorePerformanceIT test. Never uses application DB. */
@DataJpaTest(showSql=false, properties={"spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "logging.level.org.hibernate.stat=OFF", "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
@ContextConfiguration(classes=CorePerformanceIT.Config.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CorePerformanceIT {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }
    @Configuration @EntityScan(basePackageClasses=Message.class)
    @EnableJpaRepositories(basePackageClasses=MessageRepository.class)
    @Import({RoomService.class, RoomReadStateService.class, RoomAccessService.class, MessageService.class})
    static class Config {}
    @Autowired EntityManager em;
    @Autowired RoomService rooms;
    @Autowired MessageService messages;
    @Autowired RoomReadStateService reads;
    @MockitoBean UserService users;
    @MockitoBean FriendShipAccessService friends;
    @MockitoBean SimpMessagingTemplate broker;
    @MockitoBean MessageAccessService access;
    @MockitoBean S3Service storage;

    User user(String suffix) {
        User u=new User(); u.setUsername(suffix); u.setDisplayName(suffix);
        u.setEmail(suffix+"@test.invalid"); u.setPassword("synthetic"); em.persist(u); return u;
    }

    @Test void unreadSummaryPreservesMembershipAndReadBoundaries() {
        User me=user("me"), other=user("other"), outsider=user("outsider");
        when(users.getUser("me")).thenReturn(me);
        List<Long> ids=new ArrayList<>();
        for(var type:com.zvonok.model.enumeration.RoomType.values()) {
            Room room=new Room(); room.setType(type);
            room.setMembers(new ArrayList<>(List.of(me,other))); em.persist(room);
            List<Message> history=new ArrayList<>();
            for(int i=0;i<5;i++) {
                Message message=new Message(); message.setRoom(room); message.setSender(i==3?me:other);
                message.setContent("synthetic"); message.setSentAt(java.time.LocalDateTime.now());
                if(i==4) message.setDeletedAt(java.time.LocalDateTime.now());
                em.persist(message); history.add(message);
            }
            em.flush();
            var summary=reads.getUnreadSummaries(me.getId(),List.of(room.getId())).get(room.getId());
            assertEquals(3L,summary.getUnreadCount());
            assertEquals(history.getFirst().getId(),summary.getFirstUnreadMessageId());
            RoomReadState state=new RoomReadState(); state.setRoom(room); state.setUser(me);
            state.setLastReadMessageId(history.get(1).getId()); state.setUpdatedAt(java.time.LocalDateTime.now()); em.persist(state); em.flush();
            summary=reads.getUnreadSummaries(me.getId(),List.of(room.getId())).get(room.getId());
            assertEquals(1L,summary.getUnreadCount()); assertEquals(history.get(2).getId(),summary.getFirstUnreadMessageId());
            history.get(2).setContent("edit"); em.flush();
            assertEquals(1,reads.getUnreadCountsForRooms(me.getId(),List.of(room.getId())).get(room.getId()));
            history.get(2).setDeletedAt(java.time.LocalDateTime.now()); em.flush();
            assertEquals(0,reads.getUnreadCountsForRooms(me.getId(),List.of(room.getId())).get(room.getId()));
            ids.add(room.getId());
        }
        Room hidden=new Room(); hidden.setMembers(new ArrayList<>(List.of(other,outsider))); em.persist(hidden);
        Room inactive=new Room(); inactive.setIsActive(false); inactive.setMembers(new ArrayList<>(List.of(me,other))); em.persist(inactive);
        em.flush(); em.clear();
        var visible=rooms.getUserRoomsWithUnread("me");
        assertEquals(new HashSet<>(ids),new HashSet<>(visible.stream().map(r->r.getId()).toList()));
        assertTrue(visible.stream().allMatch(r->r.getMembers().size()==2));
        assertTrue(reads.getUnreadSummaries(me.getId(),List.of()).isEmpty());
    }
    @ParameterizedTest @ValueSource(ints={1,20,200})
    void listing(int count) {
        User me=user("me"), other=user("other");
        for(int i=0;i<count;i++) {
            Room r=new Room(); r.setMembers(new ArrayList<>(List.of(me,other))); em.persist(r); em.flush();
            em.createNativeQuery("insert into message(room_id,sender_id,content,type,sent_at) select :room,:sender,'synthetic','DEFAULT',current_timestamp from generate_series(1,100)")
                .setParameter("room",r.getId()).setParameter("sender",other.getId()).executeUpdate();
        }
        em.flush(); em.clear();
        when(users.getUser("me")).thenReturn(me); when(users.getUser(me.getId())).thenReturn(me);
        var stats=em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics(); stats.clear();
        long start=System.nanoTime(); var result=rooms.getUserRoomsWithUnread("me"); long elapsed=System.nanoTime()-start;
        assertEquals(count,result.size()); result.forEach(r->assertEquals(100,r.getUnreadCount()));
        System.out.printf("STAGE8,listing,%d,queries=%d,entities=%d,elapsed_ms=%.3f,hibernate_max_ms=%d%n",count,stats.getPrepareStatementCount(),stats.getEntityLoadCount(),elapsed/1e6,stats.getQueryExecutionMaxTime());
        if(Boolean.getBoolean("stage8.optimized")) assertTrue(stats.getPrepareStatementCount()<=3);
    }
    @ParameterizedTest @ValueSource(ints={100,1000,10000})
    void history(int count) {
        User me=user("me"), other=user("other"); Room r=new Room();
        r.setMembers(new ArrayList<>(List.of(me,other))); em.persist(r); em.flush();
        em.createNativeQuery("insert into message(room_id,sender_id,content,type,sent_at) select :room,:sender,'synthetic','DEFAULT',current_timestamp from generate_series(1,:count)")
            .setParameter("room",r.getId()).setParameter("sender",other.getId()).setParameter("count",count).executeUpdate();
        em.clear(); when(users.getUser("me")).thenReturn(me);
        var stats=em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics(); stats.clear();
        long start=System.nanoTime(); var result=messages.getRoomMessages("me",r.getId(),null,50); long elapsed=System.nanoTime()-start;
        assertEquals(50,result.size()); for(int i=1;i<50;i++) assertTrue(result.get(i-1).getId()<result.get(i).getId());
        System.out.printf("STAGE8,history,%d,queries=%d,entities=%d,elapsed_ms=%.3f%n",count,stats.getPrepareStatementCount(),stats.getEntityLoadCount(),elapsed/1e6);
        if(Boolean.getBoolean("stage8.optimized")) assertTrue(stats.getPrepareStatementCount()<=8);
        var plan=em.createNativeQuery("explain (analyze,buffers) select id from message where room_id=:room and deleted_at is null order by id desc limit 50").setParameter("room",r.getId()).getResultList();
        System.out.println("STAGE8,plan,history,"+count+","+plan);
        // Fresh planner statistics, separately from the unchanged before/after measurement.
        em.createNativeQuery("analyze message").executeUpdate();
        em.createNativeQuery("analyze room_reed_state").executeUpdate();
        var fresh=em.createNativeQuery("explain (analyze,buffers) select id from message where room_id=:room and deleted_at is null order by id desc limit 50").setParameter("room",r.getId()).getResultList();
        System.out.println("STAGE8,plan-analyzed,history,"+count+","+fresh);
        var unreadPlan=em.createNativeQuery("explain (analyze,buffers) select m.room_id,count(m.id),min(m.id) from message m left join room_reed_state s on s.room_id=m.room_id and s.user_id=:user where m.room_id=:room and m.sender_id<>:user and m.deleted_at is null and (s.last_read_message_id is null or m.id>s.last_read_message_id) group by m.room_id")
            .setParameter("user",me.getId()).setParameter("room",r.getId()).getResultList();
        System.out.println("STAGE8,plan-analyzed,unread,"+count+","+unreadPlan);
    }
}
