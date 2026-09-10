package com.atguigu.lease.web.app.service.impl;

import com.atguigu.lease.common.constant.RedisConstant;
import com.atguigu.lease.common.utils.CacheUtil;
import com.atguigu.lease.model.entity.ApartmentInfo;
import com.atguigu.lease.model.entity.FacilityInfo;
import com.atguigu.lease.model.entity.LabelInfo;
import com.atguigu.lease.model.enums.ItemType;
import com.atguigu.lease.web.app.mapper.*;
import com.atguigu.lease.web.app.service.ApartmentInfoService;
import com.atguigu.lease.web.app.service.LabelInfoService;
import com.atguigu.lease.web.app.vo.apartment.ApartmentDetailVo;
import com.atguigu.lease.web.app.vo.apartment.ApartmentItemVo;
import com.atguigu.lease.web.app.vo.graph.GraphVo;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author liubo
 * @description 针对表【apartment_info(公寓信息表)】的数据库操作Service实现
 * @createDate 2023-07-26 11:12:39
 */
@Service
public class ApartmentInfoServiceImpl extends ServiceImpl<ApartmentInfoMapper, ApartmentInfo>
        implements ApartmentInfoService {

    @Autowired
    private ApartmentInfoMapper apartmentInfoMapper;

    @Autowired
    private LabelInfoMapper labelInfoMapper;

    @Autowired
    private GraphInfoMapper graphInfoMapper;

    @Autowired
    private RoomInfoMapper roomInfoMapper;

    @Autowired
    private FacilityInfoMapper facilityInfoMapper;

    @Autowired
    private CacheUtil cacheUtil;

    @Override
    public ApartmentItemVo selectApartmentItemVoById(Long id) {
        //1.构建缓存Key
        String cacheKey = RedisConstant.APP_APARTMENT_ITEM_PREFIX + id;

        //2.查询缓存
        ApartmentItemVo cachedItem = cacheUtil.get(cacheKey, ApartmentItemVo.class);
        if (cachedItem != null) {
            return cachedItem;
        }

        //3.缓存未命中，查询数据库
        //3.1查出apartmentInfo
        ApartmentInfo apartmentInfo = apartmentInfoMapper.selectById(id);
        if (apartmentInfo == null) {
            //防止缓存穿透：缓存空值
            cacheUtil.set(cacheKey, null, RedisConstant.APP_APARTMENT_ITEM_TTL_SEC, TimeUnit.SECONDS);
            return null;
        }

        //3.2查出labelInfoList、graphVoList和minRent
        List<LabelInfo> labelInfoList = labelInfoMapper.selectListByApartmentId(id);
        List<GraphVo> graphVoList = graphInfoMapper.selectListByItemTypeAndId(ItemType.APARTMENT, id);
        BigDecimal minRent = roomInfoMapper.selectMinRentByApartmentId(id);

        //4.拼接apartmentItemVo
        ApartmentItemVo apartmentItemVo = new ApartmentItemVo();
        BeanUtils.copyProperties(apartmentInfo, apartmentItemVo);
        apartmentItemVo.setGraphVoList(graphVoList);
        apartmentItemVo.setLabelInfoList(labelInfoList);
        apartmentItemVo.setMinRent(minRent);

        //5.写入缓存
        cacheUtil.set(cacheKey, apartmentItemVo, RedisConstant.APP_APARTMENT_ITEM_TTL_SEC, TimeUnit.SECONDS);

        //返回apartmentItemVo
        return apartmentItemVo;
    }

    @Override
    public ApartmentDetailVo getApartmentDetailById(Long id) {
        //1.查询公寓信息
        ApartmentInfo apartmentInfo = apartmentInfoMapper.selectById(id);
        //2.查询图片信息
        List<GraphVo> graphVoList = graphInfoMapper.selectListByItemTypeAndId(ItemType.APARTMENT, id);
        //3.查询标签信息
        List<LabelInfo> labelInfoList = labelInfoMapper.selectListByApartmentId(id);
        //4.查询配套信息
        List<FacilityInfo> facilityInfoList = facilityInfoMapper.selectListByApartmentId(id);
        //5.查询最小租金
        BigDecimal minRent = roomInfoMapper.selectMinRentByApartmentId(id);

        //拼接apartmentDetailVo
        ApartmentDetailVo apartmentDetailVo = new ApartmentDetailVo();

        BeanUtils.copyProperties(apartmentInfo, apartmentDetailVo);
        apartmentDetailVo.setGraphVoList(graphVoList);
        apartmentDetailVo.setLabelInfoList(labelInfoList);
        apartmentDetailVo.setFacilityInfoList(facilityInfoList);
        apartmentDetailVo.setMinRent(minRent);

        //返回apartmentDetailVo
        return apartmentDetailVo;
    }
}




