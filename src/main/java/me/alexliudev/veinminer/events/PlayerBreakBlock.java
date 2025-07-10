package me.alexliudev.veinminer.events;

import me.alexliudev.veinminer.Veinminer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayerBreakBlock implements Listener {
    private final List<UUID> chainBreaking = new ArrayList<>();
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (chainBreaking.contains(player.getUniqueId())) return;//正在连锁，一键跳过
        if (player.getFoodLevel() <= 0) return;// 快饿死了，就别让他弄了
        if (!player.isSneaking()) return;
        if (!player.hasPermission("veinminer.user")) {
            player.sendMessage(ChatColor.BLUE+"连锁挖矿 >>> "+ChatColor.RED+"你没有权限进行连锁挖矿");
            return;
        }

        Location origin = event.getBlock().getLocation();
        Material targetType = event.getBlock().getType();

        Plugin veinMinerPlugin = Veinminer.getProvidingPlugin(Veinminer.class);

        if (veinMinerPlugin.getConfig().getBoolean("vein.has_block_white_list") && !veinMinerPlugin.getConfig().getStringList("vein.white_list_blocks").contains(targetType.toString().toLowerCase())) {
            player.sendMessage(ChatColor.BLUE+"连锁挖矿 >>> "+ChatColor.RED+"此类型方块不支持连锁，详情请联系服务器管理员");
            return;
        }//如果方块不在白名单，跳过
        event.setCancelled(true);
        player.sendMessage(ChatColor.BLUE+"连锁挖矿 >>> "+ChatColor.GREEN+"正在计算...");
        int limit = veinMinerPlugin.getConfig().getInt("vein.max_limit");
        if (limit <= 0) limit = Integer.MAX_VALUE;//无效数值=无限制

        // 异步获取所有目标
        int finalLimit = limit;
        ForkJoinPool.commonPool().submit(() -> {
            ChainTask task = new ChainTask(origin, targetType, ConcurrentHashMap.newKeySet() ,new AtomicInteger(0),finalLimit);
            List<Block> toBreak = task.invoke();

            // 配置最大连锁限制
            if (toBreak.size() > finalLimit) {
                toBreak = toBreak.subList(0, finalLimit);
            }

            List<Block> finalList = new ArrayList<>(toBreak);
            String bypassCommand = veinMinerPlugin.getConfig().getString("anticheat.bypass_command").replace("%player%", player.getName());
            String removeBypassCommand = veinMinerPlugin.getConfig().getString("anticheat.remove_bypass_command").replace("%player%", player.getName());
            chainBreaking.add(player.getUniqueId());
            new BukkitRunnable() {
                @Override
                public void run() {
                    int mines = 0;
                    if (!bypassCommand.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), bypassCommand);
                    for (Block b : finalList) {
                        if (b.getType() != targetType) continue;
                        // 给玩家上疲劳!
                        player.setExhaustion(player.getExhaustion() + 0.004f);
                        // 开始破坏方块喵
                        if (player.breakBlock(b)) mines++;
                    }
                    if (!removeBypassCommand.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), removeBypassCommand);
                    player.sendMessage(ChatColor.BLUE+"连锁挖矿 >>> "+ChatColor.GREEN+"连锁完毕，共挖掘了："+mines+"个方块");
                    chainBreaking.remove(player.getUniqueId());
                }
            }.runTask(veinMinerPlugin);
        });
    }


    private static class ChainTask extends RecursiveTask<List<Block>> {
        private final Location location;
        private final Material type;
        private final Set<Location> visited;
        private final int maxLimit;
        private final AtomicInteger blocks;

        public ChainTask(Location location, Material type, Set<Location> visited, AtomicInteger blocks, int maxLimit) {
            this.location = location;
            this.type = type;
            this.maxLimit = maxLimit;
            this.visited = visited;
            this.blocks = blocks;
        }

        @Override
        protected List<Block> compute() {
            if (!visited.add(location) || blocks.get() >= maxLimit)// 不要无限计算下去 & 跳过重复
                return Collections.emptyList();

            Block b = location.getBlock();
            if (b.getType() != type) return Collections.emptyList();// 省点内存，直接返回空列表
            List<Block> result = new ArrayList<>();
            blocks.incrementAndGet();
            result.add(b);

            List<ChainTask> subtasks = new ArrayList<>();// 尝试处理邻居
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Location next = location.clone().add(dx, dy, dz);
                        if (!visited.contains(next) && blocks.get() < maxLimit) {
                            ChainTask task = new ChainTask(next, type, visited, blocks, maxLimit);
                            task.fork();
                            subtasks.add(task);
                        }
                    }
                }
            }
            for (ChainTask task : subtasks) {
                result.addAll(task.join());
            }
            return result;
        }
    }
}
