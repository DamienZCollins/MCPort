package com.mcport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mcport.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    /**
     * 原子扣减可用库存并转入预留库存；库存不足时不更新，返回 0。
     */
    @Update("""
            UPDATE t_inventory
               SET available_qty = available_qty - #{quantity},
                   reserved_qty = reserved_qty + #{quantity}
             WHERE sku_id = #{skuId}
               AND available_qty >= #{quantity}
            """)
    int lock(@Param("skuId") String skuId, @Param("quantity") int quantity);
}
