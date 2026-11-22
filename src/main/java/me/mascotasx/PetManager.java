package me.mascotasx;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

public class PetManager {
    private final MascotasX plugin;
    private final Map<String, PetType> types = new LinkedHashMap<>();
    private final Map<UUID, Profile> profiles = new HashMap<>();
    private final Map<UUID, LivingEntity> active = new HashMap<>();
    private final Map<UUID, UUID> entityOwner = new HashMap<>();
    private final Map<UUID, LivingEntity> targets = new HashMap<>();
    private final Map<UUID, Long> lastAttack = new HashMap<>();
    private final Map<UUID, Input> inputs = new HashMap<>();
    private final File dataDir;
    private final Map<UUID, Long> targetSince = new HashMap<>();
    private final Map<UUID, Long> ignore = new HashMap<>();
    private File petsFile, effectsFile;
    private YamlConfiguration petsYaml, effectsYaml;
    private final Map<UUID, Long> windup = new HashMap<>();
    private final Map<UUID, Vector> prevPos = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Long> barUntil = new HashMap<>();
    private final Map<UUID, UUID> hitOwner = new HashMap<>();
    private final Map<UUID, Long> hitTime = new HashMap<>();
    private int tick = 0;

    public PetManager(MascotasX plugin) {
        this.plugin = plugin;
        this.dataDir = new File(plugin.getDataFolder(), "data");
        dataDir.mkdirs();
    }

    // ------------------------------------------------------------------ tipos
    public void loadTypes() {
        types.clear();
        petsFile = new File(plugin.getDataFolder(), "pets.yml");
        effectsFile = new File(plugin.getDataFolder(), "effects.yml");
        if (!effectsFile.exists()) plugin.saveResource("effects.yml", false);
        petsYaml = YamlConfiguration.loadConfiguration(petsFile);
        effectsYaml = YamlConfiguration.loadConfiguration(effectsFile);
        ConfigurationSection s = petsYaml.getConfigurationSection("pets");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(id);
            if (c == null) continue;
            EntityType et;
            try {
                et = EntityType.valueOf(c.getString("entity", "PIG").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Mascota '" + id + "': entidad invalida, se omite.");
                continue;
            }
            Material icon = Material.matchMaterial(c.getString("icon", et.name() + "_SPAWN_EGG"));
            if (icon == null) icon = Material.NAME_TAG;
            types.put(id, new PetType(id, et, c.getString("name", id), c.getDouble("price", 1000),
                    c.getDouble("damage", 2), icon, c.getInt("page", 1), c.getInt("slot", 10),
                    c.getBoolean("flying", false), c.getString("attack", "melee").toLowerCase(Locale.ROOT),
                    c.getBoolean("enabled", true)));
        }
        plugin.getLogger().info(types.size() + " mascotas cargadas.");
    }

    // ---- edicion desde el panel admin
    public void setPetValue(String id, String key, Object value) {
        petsYaml.set("pets." + id + "." + key, value);
        try { petsYaml.save(petsFile); } catch (IOException ex) { plugin.getLogger().warning("No se pudo guardar pets.yml"); }
        loadTypes();
    }

    public ConfigurationSection effectSection(String petId) {
        ConfigurationSection s = effectsYaml.getConfigurationSection("pets." + petId);
        return s != null ? s : effectsYaml.getConfigurationSection("default");
    }

    public void setEffectLevel(String petId, String key, int level) {
        if (effectsYaml.getConfigurationSection("pets." + petId) == null) {
            ConfigurationSection def = effectsYaml.getConfigurationSection("default");
            if (def != null) for (String k : def.getKeys(false)) {
                ConfigurationSection c = def.getConfigurationSection(k);
                if (c != null) for (String f : c.getKeys(false)) effectsYaml.set("pets." + petId + "." + k + "." + f, c.get(f));
            }
        }
        effectsYaml.set("pets." + petId + "." + key + ".level", Math.max(1, level));
        try { effectsYaml.save(effectsFile); } catch (IOException ex) { plugin.getLogger().warning("No se pudo guardar effects.yml"); }
    }

    public void wipe(Player p) {
        despawn(p);
        Profile pr = profile(p.getUniqueId());
        pr.owned.clear();
        pr.active = null;
        save(p.getUniqueId());
    }

    public double petDamage(Entity pet) {
        UUID o = ownerOf(pet);
        if (o == null) return 0;
        Profile pr = profiles.get(o);
        if (pr == null || pr.active == null) return 0;
        PetData d = pr.owned.get(pr.active);
        PetType t = types.get(pr.active);
        return d == null || t == null ? 0 : damage(t, d);
    }

    public PetType type(String id) { return types.get(id); }
    public Collection<PetType> types() { return types.values(); }
    public int pages() { return types.values().stream().mapToInt(PetType::page).max().orElse(1); }

    // ------------------------------------------------------------------ niveles
    public double bonus() { return plugin.getConfig().getDouble("levels.damage-bonus-percent-per-level", 2); }
    public int maxLevel() { return plugin.getConfig().getInt("levels.max", 50); }
    public double need(int level) {
        double base = plugin.getConfig().getDouble("levels.xp-base", 50);
        double per = plugin.getConfig().getDouble("levels.xp-per-level", 20);
        double curve = plugin.getConfig().getDouble("levels.xp-curve", 3);
        return Math.round(base + level * per + curve * level * level);
    }
    public double damage(PetType t, PetData d) { return t.damage() * (1 + d.level * bonus() / 100.0); }

    public void addXp(Player p, PetData d, PetType t, double amount) {
        if (d.level >= maxLevel()) return;
        d.xp += amount;
        boolean up = false;
        while (d.level < maxLevel() && d.xp >= need(d.level)) {
            d.xp -= need(d.level);
            d.level++;
            up = true;
            levelUp(p, d, t, d.level);
        }
        if (d.level >= maxLevel()) d.xp = 0;
        if (up) {
            LivingEntity e = active.get(p.getUniqueId());
            if (e != null) applyCosmetics(e, d, t);
        }
        showXp(p, d, t, amount);
        save(p.getUniqueId());
    }

    /** Muestra el progreso de XP (bossbar y/o actionbar segun config.yml: xp-bar.*). */
    public void showXp(Player p, PetData d, PetType t, double gained) {
        String mode = plugin.getConfig().getString("xp-bar.mode", "bossbar").toLowerCase(Locale.ROOT);
        boolean maxed = d.level >= maxLevel();
        double need = need(d.level);
        double frac = maxed ? 1 : Math.max(0, Math.min(1, d.xp / need));
        String text = "<yellow>" + d.displayName(t) + " <gray>Lv." + d.level + " <dark_gray>| "
                + (maxed ? "<gold>NIVEL MÁXIMO" : "<green>" + (int) d.xp + "<gray>/<green>" + (int) need + " <gray>(" + (int) (frac * 100) + "%)")
                + (gained > 0 ? " <aqua>+" + Text.num(gained) + " XP" : "");
        if (mode.equals("actionbar") || mode.equals("both")) p.sendActionBar(Text.c(text));
        if (mode.equals("bossbar") || mode.equals("both")) {
            UUID id = p.getUniqueId();
            BossBar.Color col;
            try { col = BossBar.Color.valueOf(plugin.getConfig().getString("xp-bar.color", "GREEN").toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { col = BossBar.Color.GREEN; }
            BossBar bar = bars.get(id);
            if (bar == null) {
                bar = BossBar.bossBar(Text.c(text), (float) frac, col, BossBar.Overlay.NOTCHED_10);
                bars.put(id, bar);
                p.showBossBar(bar);
            } else {
                bar.name(Text.c(text));
                bar.progress((float) frac);
                bar.color(col);
            }
            boolean always = plugin.getConfig().getBoolean("xp-bar.always", false);
            long dur = plugin.getConfig().getLong("xp-bar.duration", 6) * 1000L;
            barUntil.put(id, always ? Long.MAX_VALUE : System.currentTimeMillis() + dur);
        }
    }

    private void hideBar(Player p) {
        BossBar b = bars.remove(p.getUniqueId());
        barUntil.remove(p.getUniqueId());
        if (b != null) p.hideBossBar(b);
    }

    private void tickBars() {
        long now = System.currentTimeMillis();
        boolean always = plugin.getConfig().getBoolean("xp-bar.always", false);
        long dur = plugin.getConfig().getLong("xp-bar.duration", 6) * 1000L;
        for (Map.Entry<UUID, Long> en : new ArrayList<>(barUntil.entrySet())) {
            Player p = Bukkit.getPlayer(en.getKey());
            long until = en.getValue();
            if (p == null) { bars.remove(en.getKey()); barUntil.remove(en.getKey()); continue; }
            if (!always && until == Long.MAX_VALUE) { until = now + dur; barUntil.put(en.getKey(), until); }
            if (!always && now > until) hideBar(p);
        }
        hitTime.entrySet().removeIf(en -> now - en.getValue() > 30000);
        hitOwner.keySet().removeIf(k -> !hitTime.containsKey(k));
        if (prevPos.size() > 200) prevPos.clear();
    }

    public void markHit(Entity victim, UUID owner) {
        hitOwner.put(victim.getUniqueId(), owner);
        hitTime.put(victim.getUniqueId(), System.currentTimeMillis());
    }

    /** Dueno que dio el ultimo golpe a la victima (hasta 10 s antes de morir: fuego, etc.). */
    public UUID creditFor(Entity victim) {
        Long t = hitTime.remove(victim.getUniqueId());
        UUID o = hitOwner.remove(victim.getUniqueId());
        return t != null && System.currentTimeMillis() - t < 10000 ? o : null;
    }

    private void levelUp(Player p, PetData d, PetType t, int lvl) {
        plugin.msg(p, "level-up", "%pet%", d.displayName(t), "%level%", String.valueOf(lvl));
        p.showTitle(Title.title(Text.c("<gold>¡Nivel " + lvl + "!"), Text.c("<yellow>" + d.displayName(t)),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        ConfigurationSection r = plugin.getConfig().getConfigurationSection("levels.rewards." + lvl);
        if (r == null) return;
        double money = r.getDouble("money", 0);
        if (money > 0 && plugin.economy() != null) plugin.economy().deposit(p, money);
        for (String cmd : r.getStringList("commands"))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
    }

    public void reward(Player owner, LivingEntity victim) {
        Profile pr = profile(owner.getUniqueId());
        PetData d = pr.active == null ? null : pr.owned.get(pr.active);
        PetType t = d == null ? null : types.get(d.type);
        if (d == null || t == null) return;
        double xp = Math.max(3, victim.getMaxHealth() / 4.0) * plugin.getConfig().getDouble("levels.xp-multiplier", 1.0);
        addXp(owner, d, t, xp);
    }

    // ------------------------------------------------------------------ perfiles
    public Profile profile(UUID id) { return profiles.computeIfAbsent(id, this::loadProfile); }

    private Profile loadProfile(UUID id) {
        Profile pr = new Profile();
        File f = new File(dataDir, id + ".yml");
        if (!f.exists()) return pr;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        pr.active = y.getString("active");
        ConfigurationSection s = y.getConfigurationSection("pets");
        if (s != null) for (String pid : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(pid);
            if (c == null) continue;
            PetData d = new PetData(pid);
            d.level = c.getInt("level", 1);
            d.xp = c.getDouble("xp", 0);
            d.name = c.getString("name");
            d.muted = c.getBoolean("muted");
            d.burning = c.getBoolean("burning");
            d.aura = c.getBoolean("aura");
            d.upside = c.getBoolean("upside");
            d.baby = c.getBoolean("baby");
            d.attack = c.getBoolean("attack", true);
            d.color = c.getInt("color");
            d.variant = c.getInt("variant");
            d.effect = c.getString("effect");
            pr.owned.put(pid, d);
        }
        return pr;
    }

    public void save(UUID id) {
        Profile pr = profiles.get(id);
        if (pr == null) return;
        YamlConfiguration y = new YamlConfiguration();
        y.set("active", pr.active);
        for (PetData d : pr.owned.values()) {
            String b = "pets." + d.type + ".";
            y.set(b + "level", d.level);
            y.set(b + "xp", d.xp);
            y.set(b + "name", d.name);
            y.set(b + "muted", d.muted);
            y.set(b + "burning", d.burning);
            y.set(b + "aura", d.aura);
            y.set(b + "upside", d.upside);
            y.set(b + "baby", d.baby);
            y.set(b + "attack", d.attack);
            y.set(b + "color", d.color);
            y.set(b + "variant", d.variant);
            y.set(b + "effect", d.effect);
        }
        try { y.save(new File(dataDir, id + ".yml")); }
        catch (IOException ex) { plugin.getLogger().warning("No se pudo guardar " + id + ": " + ex.getMessage()); }
    }

    public void unload(Player p) {
        UUID id = p.getUniqueId();
        Profile pr = profiles.get(id);
        String act = pr == null ? null : pr.active;
        despawn(p);
        if (pr != null) pr.active = act; // se conserva para auto-invocar al volver
        save(id);
        profiles.remove(id);
        targets.remove(id);
        inputs.remove(id);
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(profiles.keySet())) {
            Player p = Bukkit.getPlayer(id);
            Profile pr = profiles.get(id);
            String act = pr.active;
            if (p != null) despawn(p);
            pr.active = act;
            save(id);
        }
        for (LivingEntity e : active.values()) e.remove();
        active.clear();
    }

    // ------------------------------------------------------------------ compra / invocar
    public void buy(Player p, PetType t) {
        Profile pr = profile(p.getUniqueId());
        if (pr.owned.containsKey(t.id())) { plugin.msg(p, "already-owned"); return; }
        EconomyHook eco = plugin.economy();
        if (eco != null && t.price() > 0) {
            if (!eco.has(p, t.price())) { plugin.msg(p, "no-money", "%price%", Text.money(t.price())); return; }
            eco.withdraw(p, t.price());
        }
        pr.owned.put(t.id(), new PetData(t.id()));
        save(p.getUniqueId());
        plugin.msg(p, "bought", "%pet%", t.name(), "%price%", Text.money(t.price()));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
    }

    public void give(Player p, PetType t) {
        Profile pr = profile(p.getUniqueId());
        pr.owned.putIfAbsent(t.id(), new PetData(t.id()));
        save(p.getUniqueId());
    }

    public boolean hasActive(Player p) { return active.containsKey(p.getUniqueId()); }
    public LivingEntity activeEntity(Player p) { return active.get(p.getUniqueId()); }
    public boolean isPet(Entity e) { return e != null && entityOwner.containsKey(e.getUniqueId()); }
    public UUID ownerOf(Entity e) { return e == null ? null : entityOwner.get(e.getUniqueId()); }
    public void setTarget(Player p, LivingEntity t) {
        if (hasActive(p) && !isPet(t)) { targets.put(p.getUniqueId(), t); targetSince.put(p.getUniqueId(), System.currentTimeMillis()); }
    }
    public void setInput(UUID id, Input in) { inputs.put(id, in); }

    public boolean isActive(Player p, String id) {
        Profile pr = profile(p.getUniqueId());
        return id.equals(pr.active) && hasActive(p);
    }

    public void toggle(Player p, String id) {
        if (isActive(p, id)) { despawn(p); profile(p.getUniqueId()).active = null; save(p.getUniqueId()); plugin.msg(p, "stored"); }
        else summon(p, id);
    }

    public void summon(Player p, String id) {
        Profile pr = profile(p.getUniqueId());
        PetData d = pr.owned.get(id);
        PetType t = types.get(id);
        if (d == null || t == null) return;
        despawn(p);
        Entity ent;
        try {
            ent = p.getWorld().spawnEntity(p.getLocation(), t.entity());
        } catch (Exception ex) {
            plugin.getLogger().warning("No se pudo invocar " + id + ": " + ex.getMessage());
            return;
        }
        if (!(ent instanceof LivingEntity le)) { ent.remove(); return; }
        prepare(le, t);
        active.put(p.getUniqueId(), le);
        entityOwner.put(le.getUniqueId(), p.getUniqueId());
        pr.active = id;
        applyCosmetics(le, d, t);
        save(p.getUniqueId());
        if (plugin.getConfig().getBoolean("xp-bar.always", false)) showXp(p, d, t, 0);
        plugin.msg(p, "summoned", "%pet%", d.displayName(t));
    }

    private void prepare(LivingEntity le, PetType t) {
        le.setPersistent(false);
        le.setInvulnerable(true);
        le.setRemoveWhenFarAway(false);
        le.setCanPickupItems(false);
        le.setCollidable(false);
        if (le instanceof Mob m) m.setTarget(null);
        if (le instanceof Mob m) m.setAware(false); // sin IA vanilla: la mascota solo obedece a nuestro control
        if (t.flying()) le.setGravity(false);
        if (t.attack().equals("arrow") && le.getEquipment() != null) {
            le.getEquipment().setItemInMainHand(new ItemStack(t.entity() == EntityType.PILLAGER ? Material.CROSSBOW : Material.BOW));
            le.getEquipment().setItemInMainHandDropChance(0f);
        }
        if (le instanceof Boss b && b.getBossBar() != null) b.getBossBar().setVisible(false);
    }

    public void applyCosmetics(LivingEntity e, PetData d, PetType t) {
        e.setSilent(d.muted);
        e.setVisualFire(d.burning);
        if (d.upside) {
            e.customName(Component.text("Dinnerbone"));
            e.setCustomNameVisible(false);
        } else {
            e.customName(Text.c("<yellow>" + d.displayName(t) + " <gray>[<white>Lv." + d.level + "<gray>]"));
            e.setCustomNameVisible(true);
        }
        if (e instanceof Ageable a) {
            if (d.baby) a.setBaby(); else a.setAdult();
            a.setAgeLock(true);
        }
        Cosmetics.apply(e, d);
    }

    /** Re-aplica cosmeticos a la mascota invocada del jugador (si la hay). */
    public void refresh(Player p) {
        LivingEntity e = active.get(p.getUniqueId());
        Profile pr = profile(p.getUniqueId());
        if (e == null || pr.active == null) return;
        PetData d = pr.owned.get(pr.active);
        PetType t = types.get(pr.active);
        if (d != null && t != null) applyCosmetics(e, d, t);
    }

    public void despawn(Player p) {
        UUID id = p.getUniqueId();
        hideBar(p);
        LivingEntity e = active.remove(id);
        if (e != null) {
            windup.remove(e.getUniqueId());
            entityOwner.remove(e.getUniqueId());
            if (p.getVehicle() == e) p.leaveVehicle();
            p.removePassenger(e);
            e.eject();
            e.remove();
        }
        Profile pr = profiles.get(id);
        if (pr != null) {
            pr.active = null;
            clearEffects(p, pr);
        }
    }

    public void clearEffects(Player p) { clearEffects(p, profile(p.getUniqueId())); }

    private void clearEffects(Player p, Profile pr) {
        for (PetData d : pr.owned.values()) {
            if (d.effect == null) continue;
            ConfigurationSection es = effectSection(d.type);
            ConfigurationSection c = es == null ? null : es.getConfigurationSection(d.effect);
            if (c == null) continue;
            PotionEffectType pt = effectType(c.getString("type", ""));
            if (pt != null) p.removePotionEffect(pt);
        }
    }

    private PotionEffectType effectType(String key) {
        if (key == null || key.isEmpty()) return null;
        return Registry.EFFECT.get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
    }

    // ------------------------------------------------------------------ tareas
    public void startTasks() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickPets, 20L, 1L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickEffects, 40L, 40L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickBars, 20L, 10L);
    }

    private void tickPets() {
        tick++;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, LivingEntity> en : new ArrayList<>(active.entrySet())) {
            Player p = Bukkit.getPlayer(en.getKey());
            LivingEntity e = en.getValue();
            if (p == null || !p.isOnline()) { entityOwner.remove(e.getUniqueId()); e.remove(); active.remove(en.getKey()); continue; }
            Profile pr = profile(p.getUniqueId());
            PetData d = pr.active == null ? null : pr.owned.get(pr.active);
            PetType t = d == null ? null : types.get(d.type);
            if (d == null || t == null) { despawn(p); continue; }
            if (!e.isValid()) { summon(p, pr.active); continue; } // se respawnea si desaparecio

            if (!d.burning && e.getFireTicks() > 0) e.setFireTicks(0);
            if (e instanceof Mob m && m.getTarget() != null) m.setTarget(null);

            // Montada: el jugador tiene control total
            if (e.getPassengers().contains(p)) { ride(p, e, t); continue; }
            // Sombrero: la mascota va sobre la cabeza del jugador
            if (p.getPassengers().contains(e)) continue;

            if (!e.getWorld().equals(p.getWorld()) || e.getLocation().distanceSquared(p.getLocation()) > 256) {
                e.teleport(p.getLocation());
                continue;
            }

            LivingEntity tg = d.attack ? findTarget(p, t.ranged() ? 12 : 8, now) : null;
            if (tg != null) {
                long since = targetSince.computeIfAbsent(p.getUniqueId(), k -> now);
                long ref = Math.max(since, lastAttack.getOrDefault(e.getUniqueId(), 0L));
                if (now - ref > 7000) { // lleva mucho sin poder atacarlo: lo ignora un rato
                    ignore.put(tg.getUniqueId(), now + 10000);
                    targets.remove(p.getUniqueId());
                    targetSince.remove(p.getUniqueId());
                    tg = null;
                }
            }
            if (tg != null) {
                combat(e, t, d, tg);
            } else {
                cancelWindup(e);
                follow(p, e, t);
            }
        }
    }

    private LivingEntity findTarget(Player p, double radius, long now) {
        UUID id = p.getUniqueId();
        LivingEntity cur = targets.get(id);
        if (cur != null && (!cur.isValid() || cur.isDead() || !cur.getWorld().equals(p.getWorld())
                || cur.getLocation().distanceSquared(p.getLocation()) > 400)) {
            targets.remove(id);
            targetSince.remove(id);
            cur = null;
        }
        if (cur == null && tick % 5 == 0) {
            double best = Double.MAX_VALUE;
            for (Entity o : p.getNearbyEntities(radius, 4, radius)) {
                if (o instanceof Enemy && o instanceof LivingEntity m && m.isValid() && !isPet(m)
                        && ignore.getOrDefault(m.getUniqueId(), 0L) < now) {
                    double ds = m.getLocation().distanceSquared(p.getLocation());
                    if (ds < best) { best = ds; cur = m; }
                }
            }
            if (cur != null) { targets.put(id, cur); targetSince.put(id, now); }
        }
        return cur;
    }

    private Vector velocityOf(LivingEntity tg) {
        Vector now = tg.getBoundingBox().getCenter();
        Vector prev = prevPos.put(tg.getUniqueId(), now.clone());
        if (prev == null) return new Vector();
        Vector v = now.clone().subtract(prev);
        return v.length() > 1.0 ? new Vector() : v;
    }

    private void combat(LivingEntity e, PetType t, PetData d, LivingEntity tg) {
        Location el = e.getLocation(), tl = tg.getLocation();
        double dx = tl.getX() - el.getX(), dz = tl.getZ() - el.getZ();
        Vector ec = e.getBoundingBox().getCenter(), tc = tg.getBoundingBox().getCenter();
        double dist = ec.distance(tc);
        Vector tv = velocityOf(tg);
        double reach = 1.2 + (e.getWidth() + tg.getWidth()) / 2.0 + (e.getHeight() + tg.getHeight()) / 4.0;
        boolean close = e.getBoundingBox().clone().expand(0.5).overlaps(tg.getBoundingBox()) || dist <= reach;

        if (t.ranged()) {
            boolean fangs = "fangs".equals(t.attack());
            if (!fangs && close) { // muy cerca: golpe directo garantizado
                cancelWindup(e);
                face(e, dx, dz);
                attack(e, tg, t, d);
                slowDown(e);
                return;
            }
            double range = fangs ? 10 : 16;
            if (dist <= range && e.hasLineOfSight(tg)) {
                lookAt(e, tc);
                slowDown(e);
                shoot(e, tg, t, tv);
            } else {
                cancelWindup(e);
                steer(e, t, tl, 0.34);
            }
            return;
        }
        if (e instanceof Mob m) m.setAggressive(true);
        if (close) {
            lookAt(e, tc);
            attack(e, tg, t, d);
            slowDown(e);
        } else {
            steer(e, t, tl, 0.34);
        }
    }

    private void attack(LivingEntity e, LivingEntity tg, PetType t, PetData d) {
        long now = System.currentTimeMillis();
        if (now - lastAttack.getOrDefault(e.getUniqueId(), 0L) < 900) return;
        lastAttack.put(e.getUniqueId(), now);
        e.swingMainHand();
        tg.damage(damage(t, d), e);
    }

    private void lookAt(LivingEntity e, Vector to) {
        Vector v = to.clone().subtract(e.getEyeLocation().toVector());
        float yaw = (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ()));
        float pitch = (float) Math.toDegrees(-Math.atan2(v.getY(), Math.sqrt(v.getX() * v.getX() + v.getZ() * v.getZ())));
        e.setRotation(yaw, pitch);
    }

    private void cancelWindup(LivingEntity e) {
        boolean was = windup.remove(e.getUniqueId()) != null;
        if (e instanceof Mob m) m.setAggressive(false);
        if (e instanceof Spellcaster sc && was) sc.setSpell(Spellcaster.Spell.NONE);
    }

    /** Direccion de disparo con prediccion de movimiento del objetivo y compensacion de caida. */
    private Vector aimDir(LivingEntity e, Vector tc, Vector tv, double speed, double gravity) {
        Vector from = e.getEyeLocation().toVector();
        double ticks = Math.min(20, tc.distance(from) / speed);
        Vector aim = tc.clone().add(tv.clone().multiply(ticks));
        aim.setY(aim.getY() + 0.5 * gravity * ticks * ticks);
        Vector dir = aim.subtract(from);
        return dir.lengthSquared() < 0.0001 ? e.getLocation().getDirection() : dir.normalize();
    }

    /**
     * Ataque a distancia con carga visible: la mascota se detiene, apunta, "recarga" (animacion, particulas
     * y sonido) y recien despues dispara. El dano real lo fija PetListener segun el nivel.
     */
    private void shoot(LivingEntity e, LivingEntity tg, PetType t, Vector tv) {
        long now = System.currentTimeMillis();
        String a = t.attack();
        long[] c = switch (a) {            // {tiempo de carga, enfriamiento} en ms
            case "arrow" -> new long[]{1000, 1500};
            case "fireball" -> new long[]{800, 1800};
            case "snowball" -> new long[]{350, 900};
            case "spit" -> new long[]{900, 1600};
            case "potion" -> new long[]{1100, 2200};
            case "wind" -> new long[]{1000, 2200};
            case "fangs" -> new long[]{1100, 2800};
            default -> new long[]{800, 1500};
        };
        if (now - lastAttack.getOrDefault(e.getUniqueId(), 0L) < c[1]) return;
        Long start = windup.get(e.getUniqueId());
        if (start == null) {
            windup.put(e.getUniqueId(), now);
            beginCharge(e, a);
            return;
        }
        if (now - start < c[0]) { chargeFx(e, a); return; }

        // listo: dispara
        windup.remove(e.getUniqueId());
        if (e instanceof Mob m) m.setAggressive(false);
        if (e instanceof Spellcaster sc) sc.setSpell(Spellcaster.Spell.NONE);
        lastAttack.put(e.getUniqueId(), now);
        e.swingMainHand();
        Vector tc = tg.getBoundingBox().getCenter();
        World w = e.getWorld();
        try {
            switch (a) {
                case "arrow" -> {
                    Arrow ar = e.launchProjectile(Arrow.class, aimDir(e, tc, tv, 2.2, 0.05).multiply(2.2));
                    ar.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                    w.playSound(e.getLocation(), Sound.ENTITY_SKELETON_SHOOT, 1f, 1f);
                }
                case "fireball" -> {
                    SmallFireball f = e.launchProjectile(SmallFireball.class, aimDir(e, tc, tv, 1.0, 0));
                    f.setIsIncendiary(false);
                    f.setYield(0);
                    w.playSound(e.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
                }
                case "snowball" -> {
                    e.launchProjectile(Snowball.class, aimDir(e, tc, tv, 1.6, 0.03).multiply(1.6));
                    w.playSound(e.getLocation(), Sound.ENTITY_SNOW_GOLEM_SHOOT, 1f, 1f);
                }
                case "spit" -> {
                    e.launchProjectile(LlamaSpit.class, aimDir(e, tc, tv, 1.2, 0.06).multiply(1.2));
                    w.playSound(e.getLocation(), Sound.ENTITY_LLAMA_SPIT, 1f, 1f);
                }
                case "wind" -> {
                    e.launchProjectile(WindCharge.class, aimDir(e, tc, tv, 1.0, 0));
                    w.playSound(e.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1f);
                }
                case "potion" -> {
                    ThrownPotion tp = e.launchProjectile(ThrownPotion.class, aimDir(e, tc, tv, 0.9, 0.05).multiply(0.9));
                    ItemStack it = new ItemStack(Material.SPLASH_POTION);
                    PotionMeta pm = (PotionMeta) it.getItemMeta();
                    pm.setBasePotionType(PotionType.POISON); // solo visual: el dano lo aplica PetListener
                    it.setItemMeta(pm);
                    tp.setItem(it);
                    w.playSound(e.getLocation(), Sound.ENTITY_WITCH_THROW, 1f, 1f);
                }
                case "fangs" -> {
                    Location base = e.getLocation(), tl = tg.getLocation();
                    Vector flat = new Vector(tl.getX() - base.getX(), 0, tl.getZ() - base.getZ());
                    double len = flat.length();
                    if (len > 0.01) flat.normalize();
                    int n = (int) Math.min(5, Math.ceil(len / 1.5));
                    List<Location> spots = new ArrayList<>();
                    for (int i = 1; i < n; i++) {
                        double f = i / (double) n;
                        Location fl = base.clone().add(flat.clone().multiply(len * f));
                        fl.setY(base.getY() + (tl.getY() - base.getY()) * f);
                        spots.add(fl);
                    }
                    Location predicted = tl.clone().add(tv.clone().multiply(10));
                    spots.add(tl.clone());                                   // justo donde esta el objetivo
                    if (predicted.distanceSquared(tl) > 0.64) spots.add(predicted); // y donde va a estar
                    w.playSound(e.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1f);
                    for (int i = 0; i < spots.size(); i++) {
                        Location spot = spots.get(i);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (e.isValid()) w.spawn(spot, EvokerFangs.class, f -> f.setOwner(e));
                        }, i * 2L);
                    }
                }
                default -> { }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Fallo el ataque a distancia de " + t.id() + ": " + ex.getMessage());
        }
    }

    private void beginCharge(LivingEntity e, String a) {
        if (e instanceof Mob m) m.setAggressive(true); // el esqueleto levanta el arco
        if (e instanceof Spellcaster sc && a.equals("fangs")) sc.setSpell(Spellcaster.Spell.FANGS); // brazos arriba + particulas
        Sound snd = switch (a) {
            case "arrow" -> Sound.ITEM_CROSSBOW_LOADING_START;
            case "fireball" -> Sound.ENTITY_BLAZE_BURN;
            case "wind" -> Sound.ENTITY_BREEZE_INHALE;
            case "spit" -> Sound.ENTITY_LLAMA_ANGRY;
            case "potion" -> Sound.ENTITY_WITCH_AMBIENT;
            case "fangs" -> Sound.ENTITY_EVOKER_PREPARE_ATTACK;
            default -> null;
        };
        if (snd != null) e.getWorld().playSound(e.getLocation(), snd, 0.8f, 1f);
    }

    private void chargeFx(LivingEntity e, String a) {
        if (tick % 3 != 0) return;
        Particle p = switch (a) {
            case "arrow" -> Particle.CRIT;
            case "fireball" -> Particle.FLAME;
            case "wind" -> Particle.CLOUD;
            case "spit" -> Particle.SPIT;
            case "potion" -> Particle.WITCH;
            case "snowball" -> Particle.SNOWFLAKE;
            default -> null;
        };
        if (p == null) return;
        Location l = e.getEyeLocation().add(e.getLocation().getDirection().multiply(0.5));
        e.getWorld().spawnParticle(p, l, 3, 0.15, 0.15, 0.15, 0.01);
    }

    private void slowDown(LivingEntity e) {
        Vector v = e.getVelocity();
        v.setX(v.getX() * 0.3);
        v.setZ(v.getZ() * 0.3);
        e.setVelocity(v);
    }

    /** Seguimiento: los terrestres caminan hasta el jugador; los voladores flotan a su lado, a la vista. */
    private void follow(Player p, LivingEntity e, PetType t) {
        Location pl = p.getLocation();
        if (t.flying()) {
            double yaw = Math.toRadians(pl.getYaw());
            Vector fwd = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
            Vector right = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
            double bob = Math.sin(tick / 10.0 + e.getEntityId()) * 0.15;
            Location dest = pl.clone().add(right.multiply(1.8)).add(fwd.multiply(1.3)).add(0, 1.7 + bob, 0);
            steer(e, t, dest, 0.45);
            return;
        }
        double dx = pl.getX() - e.getLocation().getX();
        double dz = pl.getZ() - e.getLocation().getZ();
        double h = Math.sqrt(dx * dx + dz * dz);
        if (h > 2.8 || Math.abs(pl.getY() - e.getLocation().getY()) > 2) {
            steer(e, t, pl, Math.min(0.48, 0.24 + h * 0.02));
        } else {
            slowDown(e);
        }
    }

    /** Movimiento propio por velocidad (no usa pathfinding vanilla, asi funciona con slimes, voladores, etc.). */
    private void steer(LivingEntity e, PetType t, Location dest, double speed) {
        Location l = e.getLocation();
        double dx = dest.getX() - l.getX(), dy = dest.getY() - l.getY(), dz = dest.getZ() - l.getZ();
        Vector v = e.getVelocity();
        if (t.flying()) {
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < 0.3) { e.setVelocity(v.multiply(0.5)); return; }
            double s = Math.min(speed, dist * 0.2 + 0.03);
            e.setVelocity(new Vector(dx, dy, dz).multiply(s / dist));
            face(e, dx, dz);
            return;
        }
        double h = Math.sqrt(dx * dx + dz * dz);
        if (h < 0.15) return;
        v.setX(dx / h * speed);
        v.setZ(dz / h * speed);
        if (e.isOnGround()) {
            double reach = e.getWidth() / 2 + 0.6;
            Location ahead = l.clone().add(dx / h * reach, 0.2, dz / h * reach);
            if (e instanceof Slime || !ahead.getBlock().isPassable() || (dy > 0.8 && h < 2.5)) v.setY(0.42);
        }
        if (e.isInWater()) v.setY(Math.max(v.getY(), 0.12));
        e.setVelocity(v);
        face(e, dx, dz);
    }

    private void face(LivingEntity e, double dx, double dz) {
        if (dx * dx + dz * dz > 0.0001) e.setRotation((float) Math.toDegrees(Math.atan2(-dx, dz)), 0);
    }

    /** Control total del jugador sobre la mascota montada (WASD, salto, correr; voladores van hacia donde miras). */
    private void ride(Player p, LivingEntity e, PetType t) {
        Input in = inputs.get(p.getUniqueId());
        Vector v = e.getVelocity();
        Location pl = p.getLocation();
        double f = 0, r = 0;
        boolean jump = false, sprint = false;
        if (in != null) {
            f = (in.isForward() ? 1 : 0) - (in.isBackward() ? 1 : 0);
            r = (in.isRight() ? 1 : 0) - (in.isLeft() ? 1 : 0);
            jump = in.isJump();
            sprint = in.isSprint();
        }
        double speed = sprint ? 0.42 : 0.28;
        double yaw = Math.toRadians(pl.getYaw());
        Vector fwd = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        Vector right = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
        if (t.flying()) {
            Vector look = pl.getDirection();
            Vector dir = look.clone().multiply(f).add(right.clone().multiply(r));
            if (jump) dir.add(new Vector(0, 0.8, 0));
            if (dir.lengthSquared() > 0.0001) e.setVelocity(dir.normalize().multiply(speed));
            else e.setVelocity(v.multiply(0.4));
        } else {
            Vector dir = fwd.multiply(f).add(right.multiply(r));
            boolean moving = dir.lengthSquared() > 0.0001;
            if (moving) { dir.normalize().multiply(speed); v.setX(dir.getX()); v.setZ(dir.getZ()); }
            else { v.setX(0); v.setZ(0); }
            if (e.isOnGround()) {
                if (jump) v.setY(0.52);
                else if (moving && e instanceof Slime) v.setY(0.42);
            }
            if (e.isInWater() && jump) v.setY(0.25);
            e.setVelocity(v);
        }
        e.setRotation(pl.getYaw(), 0);
    }

    private void tickEffects() {
        for (Map.Entry<UUID, LivingEntity> en : active.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null) continue;
            Profile pr = profile(p.getUniqueId());
            PetData d = pr.active == null ? null : pr.owned.get(pr.active);
            if (d == null || d.effect == null) continue;
            ConfigurationSection es = effectSection(d.type);
            ConfigurationSection c = es == null ? null : es.getConfigurationSection(d.effect);
            if (c == null || d.level < c.getInt("level", 1)) continue;
            PotionEffectType pt = effectType(c.getString("type"));
            if (pt != null) p.addPotionEffect(new PotionEffect(pt, 140, c.getInt("amplifier", 0), true, false, true));
        }
    }
}
