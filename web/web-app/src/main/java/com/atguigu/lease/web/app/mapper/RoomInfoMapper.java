package com.atguigu.lease.web.app.mapper;

import com.atguigu.lease.model.entity.RoomInfo;
import com.atguigu.lease.web.app.vo.room.RoomItemVo;
import com.atguigu.lease.web.app.vo.room.RoomQueryVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
* @author liubo
* @description 针对表【room_info(房间信息表)】的数据库操作Mapper
* @createDate 2023-07-26 11:12:39
* @Entity com.atguigu.lease.model.entity.RoomInfo
*/
public interface RoomInfoMapper extends BaseMapper<RoomInfo> {

    IPage<RoomItemVo> pageRoomItemByQuery(Page<RoomItemVo> page, RoomQueryVo queryVo);

    BigDecimal selectMinRentByApartmentId(Long id);

    IPage<RoomItemVo> pageItemByApartmentId(IPage<RoomItemVo> page, Long id);

    @Select("""
            SELECT ri.* FROM room_info ri
            WHERE ri.id = #{id} AND ri.is_release = 1 AND ri.is_deleted = 0
              AND NOT EXISTS (
                SELECT 1 FROM lease_agreement la
                WHERE la.room_id = ri.id AND la.is_deleted = 0 AND la.status IN (2, 5)
              )
            FOR UPDATE
            """)
    RoomInfo selectReleasedByIdForUpdate(Long id);
}
