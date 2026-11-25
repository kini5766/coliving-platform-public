package com.coliving.platform.property.repository;

import com.coliving.platform.property.domain.Room;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByIdAndDeletedFalse(Long id);

    List<Room> findByBuildingIdAndDeletedFalseOrderByRoomName(Long buildingId);

    List<Room> findByBuildingIdInAndDeletedFalse(Collection<Long> buildingIds);
}
