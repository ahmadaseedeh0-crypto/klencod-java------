package com.example.mygame;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends Activity {

    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        gameView = new GameView(this);
        setContentView(gameView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameView != null) gameView.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameView != null) gameView.pause();
    }

    // =========================================================================
    // محرك اللعبة الأساسي (SurfaceView)
    // =========================================================================
    public static class GameView extends SurfaceView implements Runnable, SurfaceHolder.Callback {

        private Thread gameThread;
        private boolean isPlaying = false;
        private SurfaceHolder holder;
        private Paint paint;
        private ToneGenerator toneGen;

        // دقة الريندر الداخلي (Raycasting Resolution)
        private final int RENDER_W = 320;
        private final int RENDER_H = 200;
        private int[] framePixels;
        private Bitmap renderBitmap;
        private double[] zBuffer;

        // حالة شاشة اللمس
        private int screenW, screenH;

        // خصائص اللاعب
        private double posX = 2.5, posY = 2.5;
        private double dirX = 1.0, dirY = 0.0;
        private double planeX = 0.0, planeY = 0.66;
        private int playerHealth = 100;
        private int playerMaxHealth = 100;
        private int playerArmor = 50;
        private int playerCoins = 150;
        private int grenadeCount = 3;
        private boolean isDead = false;

        // الأسلحة
        public static class Weapon {
            String name;
            int damage;
            int ammo;
            int maxClip;
            int totalAmmo;
            int cost;
            boolean owned;
            long fireRateMs;
            long lastFired;

            public Weapon(String name, int damage, int maxClip, int totalAmmo, int cost, boolean owned, long fireRateMs) {
                this.name = name;
                this.damage = damage;
                this.maxClip = maxClip;
                this.ammo = maxClip;
                this.totalAmmo = totalAmmo;
                this.cost = cost;
                this.owned = owned;
                this.fireRateMs = fireRateMs;
                this.lastFired = 0;
            }
        }

        private Weapon[] weapons;
        private int currentWeaponIndex = 0;
        private boolean isReloading = false;
        private long reloadStartTime = 0;

        // القنابل والانفجارات
        public static class Grenade {
            double x, y;
            double vx, vy;
            long spawnTime;
            boolean exploded = false;

            public Grenade(double x, double y, double vx, double vy) {
                this.x = x;
                this.y = y;
                this.vx = vx;
                this.vy = vy;
                this.spawnTime = System.currentTimeMillis();
            }
        }
        private List<Grenade> activeGrenades = new ArrayList<>();

        // الجزيئات (Particles)
        public static class Particle {
            float x, y;
            float vx, vy;
            int color;
            int life;
            public Particle(float x, float y, float vx, float vy, int color, int life) {
                this.x = x;
                this.y = y;
                this.vx = vx;
                this.vy = vy;
                this.color = color;
                this.life = life;
            }
        }
        private List<Particle> particles = new ArrayList<>();

        // الأعداء
        public static class Enemy {
            double x, y;
            int health;
            int maxHealth;
            int damage;
            int reward;
            boolean isDead = false;
            double distance;
            long lastAttackTime;

            public Enemy(double x, double y, int health, int damage, int reward) {
                this.x = x;
                this.y = y;
                this.health = health;
                this.maxHealth = health;
                this.damage = damage;
                this.reward = reward;
                this.lastAttackTime = 0;
            }
        }
        private List<Enemy> enemies = new ArrayList<>();

        // المراحل والخرائط (0 = فراغ، 1 = جدار باطون، 2 = جدار حديد، 3 = جدار طوب)
        private int currentStage = 1;
        private final int TOTAL_STAGES = 3;
        private int[][] currentMap;

        private final int[][] STAGE_1_MAP = {
                {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1},
                {1,0,0,0,0,0,1,0,0,0,0,0,0,0,0,1},
                {1,0,1,1,0,0,1,0,1,1,1,0,1,1,0,1},
                {1,0,1,0,0,0,0,0,0,0,1,0,0,1,0,1},
                {1,0,1,0,1,1,1,1,1,0,1,1,0,1,0,1},
                {1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1},
                {1,1,1,0,1,0,0,0,1,0,1,1,1,1,0,1},
                {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
                {1,0,1,1,1,0,1,1,1,1,0,1,1,1,0,1},
                {1,0,0,0,1,0,0,0,0,1,0,1,0,0,0,1},
                {1,1,1,0,1,1,1,0,0,1,0,1,0,1,1,1},
                {1,0,0,0,0,0,1,0,0,0,0,0,0,0,0,1},
                {1,0,1,1,1,0,1,1,1,1,1,1,1,1,0,1},
                {1,0,1,0,0,0,0,0,0,0,0,0,0,1,0,1},
                {1,0,0,0,1,1,1,0,0,0,0,0,0,0,0,1},
                {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1}
        };

        private final int[][] STAGE_2_MAP = {
                {2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2},
                {2,0,0,0,2,0,0,0,0,0,0,2,0,0,0,2},
                {2,0,2,0,2,0,2,2,2,2,0,2,0,2,0,2},
                {2,0,2,0,0,0,2,0,0,2,0,0,0,2,0,2},
                {2,0,2,2,2,0,2,0,0,2,0,2,2,2,0,2},
                {2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2},
                {2,2,2,0,2,2,0,2,2,0,2,2,0,2,2,2},
                {2,0,0,0,2,0,0,0,0,0,0,2,0,0,0,2},
                {2,0,2,0,2,2,2,0,0,2,2,2,0,2,0,2},
                {2,0,2,0,0,0,0,0,0,0,0,0,0,2,0,2},
                {2,0,2,2,2,0,2,2,2,2,0,2,2,2,0,2},
                {2,0,0,0,2,0,0,0,0,0,0,2,0,0,0,2},
                {2,2,2,0,2,0,2,2,2,2,0,2,0,2,2,2},
                {2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2},
                {2,0,2,2,2,2,2,0,0,2,2,2,2,2,0,2},
                {2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2}
        };

        // واجهات التحكم واللمس
        private boolean isShopOpen = false;
        private float touchStartX, touchStartY;
        private float moveForward = 0;
        private float moveStrafe = 0;
        private float turnAngle = 0;
        private boolean isFiring = false;

        public GameView(Context context) {
            super(context);
            holder = getHolder();
            holder.addCallback(this);
            paint = new Paint();
            paint.setAntiAlias(true);

            framePixels = new int[RENDER_W * RENDER_H];
            renderBitmap = Bitmap.createBitmap(RENDER_W, RENDER_H, Bitmap.Config.RGB_565);
            zBuffer = new double[RENDER_W];

            try {
                toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 60);
            } catch (Exception ignored) {}

            initWeapons();
            loadStage(currentStage);
        }

        private void initWeapons() {
            weapons = new Weapon[]{
                    new Weapon("Pistol 9mm", 22, 12, 120, 0, true, 250),
                    new Weapon("Shotgun 12G", 65, 8, 48, 250, false, 650),
                    new Weapon("Assault Rifle", 34, 30, 180, 500, false, 110),
                    new Weapon("Rocket Launcher", 160, 4, 12, 1000, false, 900)
            };
            currentWeaponIndex = 0;
        }

        private void loadStage(int stage) {
            currentStage = stage;
            if (stage == 1) currentMap = STAGE_1_MAP;
            else currentMap = STAGE_2_MAP;

            posX = 2.5;
            posY = 2.5;
            dirX = 1.0;
            dirY = 0.0;
            planeX = 0.0;
            planeY = 0.66;

            enemies.clear();
            activeGrenades.clear();
            particles.clear();

            // توزيع الأعداء حسب المرحلة
            if (stage == 1) {
                enemies.add(new Enemy(5.5, 3.5, 70, 10, 40));
                enemies.add(new Enemy(8.5, 7.5, 90, 12, 50));
                enemies.add(new Enemy(12.5, 3.5, 110, 15, 60));
                enemies.add(new Enemy(13.5, 13.5, 180, 20, 120)); // زعيم المرحلة 1
            } else {
                enemies.add(new Enemy(3.5, 7.5, 120, 15, 70));
                enemies.add(new Enemy(7.5, 3.5, 130, 18, 80));
                enemies.add(new Enemy(10.5, 11.5, 150, 20, 100));
                enemies.add(new Enemy(13.5, 3.5, 200, 25, 150));
                enemies.add(new Enemy(13.5, 13.5, 350, 30, 300)); // زعيم المرحلة 2
            }
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            resume();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            screenW = width;
            screenH = height;
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            pause();
        }

        public void resume() {
            isPlaying = true;
            gameThread = new Thread(this);
            gameThread.start();
        }

        public void pause() {
            isPlaying = false;
            try {
                if (gameThread != null) gameThread.join();
            } catch (InterruptedException ignored) {}
        }

        @Override
        public void run() {
            long lastTime = System.currentTimeMillis();
            while (isPlaying) {
                long now = System.currentTimeMillis();
                long delta = now - lastTime;
                lastTime = now;

                update(delta);
                draw();

                try {
                    Thread.sleep(16); // ~60 إطار في الثانية
                } catch (InterruptedException ignored) {}
            }
        }

        // =========================================================================
        // تحديث منطق اللعبة والفيزياء (Update Loop)
        // =========================================================================
        private void update(long delta) {
            if (isDead || isShopOpen) return;

            // 1. تدوير اللاعب
            if (Math.abs(turnAngle) > 0.001) {
                double rotSpeed = turnAngle * 0.05;
                double oldDirX = dirX;
                dirX = dirX * Math.cos(rotSpeed) - dirY * Math.sin(rotSpeed);
                dirY = oldDirX * Math.sin(rotSpeed) + dirY * Math.cos(rotSpeed);

                double oldPlaneX = planeX;
                planeX = planeX * Math.cos(rotSpeed) - planeY * Math.sin(rotSpeed);
                planeY = oldPlaneX * Math.sin(rotSpeed) + planeY * Math.cos(rotSpeed);
                turnAngle = 0;
            }

            // 2. حركة اللاعب للأمام والخلف والجوانب مع فحص الاصطدام (Collision Detection)
            double moveSpeed = 0.06;
            if (moveForward != 0) {
                double newX = posX + dirX * moveForward * moveSpeed;
                double newY = posY + dirY * moveForward * moveSpeed;
                if (currentMap[(int) newX][(int) posY] == 0) posX = newX;
                if (currentMap[(int) posX][(int) newY] == 0) posY = newY;
            }
            if (moveStrafe != 0) {
                double strafeDirX = -dirY;
                double strafeDirY = dirX;
                double newX = posX + strafeDirX * moveStrafe * moveSpeed;
                double newY = posY + strafeDirY * moveStrafe * moveSpeed;
                if (currentMap[(int) newX][(int) posY] == 0) posX = newX;
                if (currentMap[(int) posX][(int) newY] == 0) posY = newY;
            }

            // 3. إعادة تعبئة الذخيرة
            if (isReloading && System.currentTimeMillis() - reloadStartTime > 1200) {
                Weapon w = weapons[currentWeaponIndex];
                int needed = w.maxClip - w.ammo;
                int canTake = Math.min(needed, w.totalAmmo);
                w.ammo += canTake;
                w.totalAmmo -= canTake;
                isReloading = false;
            }

            // 4. إطلاق النار المستمر إذا كان الرشاش
            if (isFiring) {
                fireCurrentWeapon();
                if (weapons[currentWeaponIndex].name.equals("Pistol 9mm") ||
                        weapons[currentWeaponIndex].name.equals("Shotgun 12G")) {
                    isFiring = false;
                }
            }

            // 5. تحديث القنابل
            Iterator<Grenade> gIterator = activeGrenades.iterator();
            while (gIterator.hasNext()) {
                Grenade g = gIterator.next();
                double nextGX = g.x + g.vx * 0.05;
                double nextGY = g.y + g.vy * 0.05;

                if (currentMap[(int) nextGX][(int) g.y] == 0) g.x = nextGX; else g.vx = -g.vx * 0.6;
                if (currentMap[(int) g.x][(int) nextGY] == 0) g.y = nextGY; else g.vy = -g.vy * 0.6;

                g.vx *= 0.95;
                g.vy *= 0.95;

                if (System.currentTimeMillis() - g.spawnTime > 2000 && !g.exploded) {
                    explodeGrenade(g);
                    g.exploded = true;
                    gIterator.remove();
                }
            }

            // 6. ذكاء اصطناعي للأعداء (AI & Attacks)
            boolean allDead = true;
            for (Enemy enemy : enemies) {
                if (enemy.isDead) continue;
                allDead = false;

                double dx = posX - enemy.x;
                double dy = posY - enemy.y;
                double dist = Math.sqrt(dx * dx + dy * dy);
                enemy.distance = dist;

                // تحرك العدو نحو اللاعب إذا كان يراه
                if (dist > 1.2 && dist < 9.0) {
                    double speed = 0.025;
                    double moveX = (dx / dist) * speed;
                    double moveY = (dy / dist) * speed;
                    if (currentMap[(int) (enemy.x + moveX)][(int) enemy.y] == 0) enemy.x += moveX;
                    if (currentMap[(int) enemy.x][(int) (enemy.y + moveY)] == 0) enemy.y += moveY;
                }

                // هجوم العدو على اللاعب
                if (dist <= 1.5 && System.currentTimeMillis() - enemy.lastAttackTime > 1000) {
                    takePlayerDamage(enemy.damage);
                    enemy.lastAttackTime = System.currentTimeMillis();
                }
            }

            // الانتقال للمرحلة التالية إذا مات كل الأعداء
            if (allDead && enemies.size() > 0) {
                if (currentStage < TOTAL_STAGES) {
                    playerCoins += 150;
                    loadStage(currentStage + 1);
                }
            }

            // 7. تحديث الجزيئات
            Iterator<Particle> pIter = particles.iterator();
            while (pIter.hasNext()) {
                Particle p = pIter.next();
                p.x += p.vx;
                p.y += p.vy;
                p.life--;
                if (p.life <= 0) pIter.remove();
            }
        }

        // =========================================================================
        // محرك الريندر 3D (Raycasting Engine)
        // =========================================================================
        private void render3D() {
            // مسح الشاشة وسقف وأرضية بلونين مختلفين
            int ceilingColor = 0xFF222222;
            int floorColor = 0xFF444444;
            for (int y = 0; y < RENDER_H; y++) {
                int col = (y < RENDER_H / 2) ? ceilingColor : floorColor;
                for (int x = 0; x < RENDER_W; x++) {
                    framePixels[y * RENDER_W + x] = col;
                }
            }

            // ريندر الجدران بواسطة DDA Algorithm
            for (int x = 0; x < RENDER_W; x++) {
                double cameraX = 2 * x / (double) RENDER_W - 1;
                double rayDirX = dirX + planeX * cameraX;
                double rayDirY = dirY + planeY * cameraX;

                int mapX = (int) posX;
                int mapY = (int) posY;

                double sideDistX, sideDistY;
                double deltaDistX = (rayDirX == 0) ? 1e30 : Math.abs(1 / rayDirX);
                double deltaDistY = (rayDirY == 0) ? 1e30 : Math.abs(1 / rayDirY);
                double perpWallDist;

                int stepX, stepY;
                int hit = 0;
                int side = 0;

                if (rayDirX < 0) {
                    stepX = -1;
                    sideDistX = (posX - mapX) * deltaDistX;
                } else {
                    stepX = 1;
                    sideDistX = (mapX + 1.0 - posX) * deltaDistX;
                }
                if (rayDirY < 0) {
                    stepY = -1;
                    sideDistY = (posY - mapY) * deltaDistY;
                } else {
                    stepY = 1;
                    sideDistY = (mapY + 1.0 - posY) * deltaDistY;
                }

                while (hit == 0) {
                    if (sideDistX < sideDistY) {
                        sideDistX += deltaDistX;
                        mapX += stepX;
                        side = 0;
                    } else {
                        sideDistY += deltaDistY;
                        mapY += stepY;
                        side = 1;
                    }
                    if (currentMap[mapX][mapY] > 0) hit = 1;
                }

                if (side == 0) perpWallDist = (sideDistX - deltaDistX);
                else perpWallDist = (sideDistY - deltaDistY);

                zBuffer[x] = perpWallDist;

                int lineHeight = (int) (RENDER_H / perpWallDist);
                int drawStart = -lineHeight / 2 + RENDER_H / 2;
                if (drawStart < 0) drawStart = 0;
                int drawEnd = lineHeight / 2 + RENDER_H / 2;
                if (drawEnd >= RENDER_H) drawEnd = RENDER_H - 1;

                int wallType = currentMap[mapX][mapY];
                int baseColor;
                switch (wallType) {
                    case 1: baseColor = 0xFF9E9E9E; break; // باطون
                    case 2: baseColor = 0xFF5D4037; break; // حديد
                    default: baseColor = 0xFFE65100; break;
                }
                if (side == 1) {
                    // تظليل الجانب عشان يعطي بُعد ثلاثي أبعاد حقيقي
                    int r = (int) (((baseColor >> 16) & 0xFF) * 0.75);
                    int g = (int) (((baseColor >> 8) & 0xFF) * 0.75);
                    int b = (int) ((baseColor & 0xFF) * 0.75);
                    baseColor = (0xFF000000) | (r << 16) | (g << 8) | b;
                }

                for (int y = drawStart; y <= drawEnd; y++) {
                    framePixels[y * RENDER_W + x] = baseColor;
                }
            }

            // ريندر الأعداء 3D وترتيبهم من الأبعد للأقرب
            renderSprites();

            renderBitmap.setPixels(framePixels, 0, RENDER_W, 0, 0, RENDER_W, RENDER_H);
        }

        private void renderSprites() {
            List<Enemy> sorted = new ArrayList<>(enemies);
            for (Enemy e : sorted) {
                e.distance = ((posX - e.x) * (posX - e.x) + (posY - e.y) * (posY - e.y));
            }
            Collections.sort(sorted, new Comparator<Enemy>() {
                @Override
                public int compare(Enemy o1, Enemy o2) {
                    return Double.compare(o2.distance, o1.distance);
                }
            });

            for (Enemy enemy : sorted) {
                if (enemy.isDead) continue;

                double spriteX = enemy.x - posX;
                double spriteY = enemy.y - posY;

                double invDet = 1.0 / (planeX * dirY - dirX * planeY);
                double transformX = invDet * (dirY * spriteX - dirX * spriteY);
                double transformY = invDet * (-planeY * spriteX + planeX * spriteY);

                if (transformY <= 0.1) continue;

                int spriteScreenX = (int) ((RENDER_W / 2) * (1 + transformX / transformY));
                int spriteHeight = Math.abs((int) (RENDER_H / transformY));
                int drawStartY = -spriteHeight / 2 + RENDER_H / 2;
                if (drawStartY < 0) drawStartY = 0;
                int drawEndY = spriteHeight / 2 + RENDER_H / 2;
                if (drawEndY >= RENDER_H) drawEndY = RENDER_H - 1;

                int spriteWidth = Math.abs((int) (RENDER_H / transformY));
                int drawStartX = -spriteWidth / 2 + spriteScreenX;
                if (drawStartX < 0) drawStartX = 0;
                int drawEndX = spriteWidth / 2 + spriteScreenX;
                if (drawEndX >= RENDER_W) drawEndX = RENDER_W - 1;

                int enemyColor = (enemy.health > 150) ? 0xFFFF0055 : 0xFF00FF66; // الزعيم لونه مختلف

                for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
                    if (transformY > 0 && stripe >= 0 && stripe < RENDER_W && transformY < zBuffer[stripe]) {
                        for (int y = drawStartY; y < drawEndY; y++) {
                            framePixels[y * RENDER_W + stripe] = enemyColor;
                        }
                    }
                }
            }
        }

        // =========================================================================
        // الرسم الرئيسي (Canvas Rendering)
        // =========================================================================
        private void draw() {
            if (!holder.getSurface().isValid()) return;
            Canvas canvas = holder.lockCanvas();
            if (canvas == null) return;

            // 1. رسم عالم الـ 3D
            render3D();
            Rect srcRect = new Rect(0, 0, RENDER_W, RENDER_H);
            Rect dstRect = new Rect(0, 0, screenW, screenH);
            canvas.drawBitmap(renderBitmap, srcRect, dstRect, null);

            // 2. رسم تأثيرات الانفجار والجزيئات
            paint.setStyle(Paint.Style.FILL);
            for (Particle p : particles) {
                paint.setColor(p.color);
                canvas.drawCircle(p.x, p.y, 6, paint);
            }

            // 3. رسم السلاح الحالي في يد اللاعب
            drawPlayerWeapon(canvas);

            // 4. رسم شاشات الـ HUD (الدم، الفلوس، الذخيرة، الرادار)
            drawHUD(canvas);

            // 5. رسم أزرار التحكم الافتراضية
            drawControls(canvas);

            // 6. رسم المتجر إذا كان مفتوحاً
            if (isShopOpen) {
                drawShopMenu(canvas);
            }

            // 7. شاشة الموت
            if (isDead) {
                paint.setColor(0xCC990000);
                canvas.drawRect(0, 0, screenW, screenH, paint);
                paint.setColor(Color.WHITE);
                paint.setTextSize(70);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("مِتّ في المعركة!", screenW / 2f, screenH / 2f - 40, paint);
                paint.setTextSize(35);
                canvas.drawText("المس الشاشة لإعادة المحاولة", screenW / 2f, screenH / 2f + 50, paint);
            }

            holder.unlockCanvasAndPost(canvas);
        }

        private void drawPlayerWeapon(Canvas canvas) {
            paint.setColor(0xFF1E1E1E);
            float gunW = screenW * 0.16f;
            float gunH = screenH * 0.35f;
            float gunX = screenW / 2f - gunW / 2f;
            float gunY = screenH - gunH;

            // مجسم السلاح
            canvas.drawRect(gunX, gunY, gunX + gunW, gunY + gunH, paint);
            paint.setColor(Color.DKGRAY);
            canvas.drawRect(gunX + 15, gunY - 30, gunX + gunW - 15, gunY, paint);

            // مؤشر تصويب (Crosshair)
            paint.setColor(Color.RED);
            paint.setStrokeWidth(4);
            float cx = screenW / 2f;
            float cy = screenH / 2f;
            canvas.drawLine(cx - 20, cy, cx + 20, cy, paint);
            canvas.drawLine(cx, cy - 20, cx, cy + 20, paint);
        }

        private void drawHUD(Canvas canvas) {
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(34);
            paint.setStyle(Paint.Style.FILL);

            // شريط الدم والدرع
            paint.setColor(0x88000000);
            canvas.drawRoundRect(20, 20, 420, 190, 15, 15, paint);

            paint.setColor(Color.RED);
            canvas.drawText("الصحة: " + playerHealth + " / " + playerMaxHealth, 40, 65, paint);

            paint.setColor(Color.CYAN);
            canvas.drawText("الدرع: " + playerArmor, 40, 110, paint);

            paint.setColor(Color.YELLOW);
            canvas.drawText("الذهب: " + playerCoins + " 💰", 40, 155, paint);

            // معلومات السلاح والمرحلة
            Weapon w = weapons[currentWeaponIndex];
            paint.setColor(0x88000000);
            canvas.drawRoundRect(screenW - 360, 20, screenW - 20, 190, 15, 15, paint);

            paint.setColor(Color.WHITE);
            canvas.drawText("المرحلة: " + currentStage, screenW - 340, 65, paint);
            paint.setColor(Color.GREEN);
            canvas.drawText(w.name, screenW - 340, 110, paint);
            paint.setColor(Color.YELLOW);
            String ammoText = isReloading ? "جاري التعبئة..." : (w.ammo + " / " + w.totalAmmo);
            canvas.drawText("الرصاص: " + ammoText, screenW - 340, 155, paint);

            // رادار / ميني ماب مصغر
            drawMiniMap(canvas);
        }

        private void drawMiniMap(Canvas canvas) {
            float mapScale = 8;
            float mapStartX = 20;
            float mapStartY = 210;

            paint.setColor(0x99000000);
            canvas.drawRect(mapStartX, mapStartY, mapStartX + 16 * mapScale, mapStartY + 16 * mapScale, paint);

            // رسم الجدران
            paint.setColor(Color.LTGRAY);
            for (int r = 0; r < 16; r++) {
                for (int c = 0; c < 16; c++) {
                    if (currentMap[r][c] > 0) {
                        canvas.drawRect(mapStartX + c * mapScale, mapStartY + r * mapScale,
                                mapStartX + (c + 1) * mapScale, mapStartY + (r + 1) * mapScale, paint);
                    }
                }
            }

            // رسم اللاعب
            paint.setColor(Color.BLUE);
            canvas.drawCircle(mapStartX + (float) posY * mapScale, mapStartY + (float) posX * mapScale, 4, paint);

            // رسم الأعداء
            paint.setColor(Color.RED);
            for (Enemy e : enemies) {
                if (!e.isDead) {
                    canvas.drawCircle(mapStartX + (float) e.y * mapScale, mapStartY + (float) e.x * mapScale, 3, paint);
                }
            }
        }

        private void drawControls(Canvas canvas) {
            paint.setStyle(Paint.Style.FILL);

            // زر إطلاق النار
            paint.setColor(0x88FF0000);
            canvas.drawCircle(screenW - 130, screenH - 130, 85, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(36);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("طَخ 💥", screenW - 130, screenH - 120, paint);

            // زر رمي القنبلة
            paint.setColor(0x88FF8800);
            canvas.drawCircle(screenW - 290, screenH - 110, 65, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(26);
            canvas.drawText("قنبلة (" + grenadeCount + ")", screenW - 290, screenH - 100, paint);

            // زر المتجر
            paint.setColor(0x889C27B0);
            canvas.drawRoundRect(screenW / 2f - 90, 20, screenW / 2f + 90, 90, 15, 15, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(28);
            canvas.drawText("المتجر 🛒", screenW / 2f, 62, paint);

            // زر تبديل السلاح
            paint.setColor(0x88009688);
            canvas.drawCircle(screenW - 120, screenH - 290, 60, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(24);
            canvas.drawText("تبديل 🔄", screenW - 120, screenH - 280, paint);

            // زر تعبئة الذخيرة (Reload)
            paint.setColor(0x88607D8B);
            canvas.drawCircle(screenW - 250, screenH - 250, 55, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(24);
            canvas.drawText("تعبئة ⚙️", screenW - 250, screenH - 240, paint);

            // منطقة عصا التحكم الوهمية (Joystick Pad)
            paint.setColor(0x33FFFFFF);
            canvas.drawCircle(180, screenH - 180, 120, paint);
            paint.setColor(0x66FFFFFF);
            canvas.drawCircle(180, screenH - 180, 50, paint);
        }

        private void drawShopMenu(Canvas canvas) {
            paint.setColor(0xEE111111);
            canvas.drawRect(80, 80, screenW - 80, screenH - 80, paint);

            paint.setColor(Color.YELLOW);
            paint.setTextSize(45);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("متجر الأسلحة والعتاد 🛒 (معك: " + playerCoins + " 💰)", screenW / 2f, 150, paint);

            paint.setTextSize(32);
            paint.setTextAlign(Paint.Align.LEFT);

            float startY = 230;
            // عرض بضائع المتجر
            paint.setColor(Color.WHITE);
            canvas.drawText("1. شوزن (Shotgun) - السعر: 250 💰 " + (weapons[1].owned ? "[تم الشراء]" : "[شراء]"), 130, startY, paint);
            canvas.drawText("2. رشاش (Assault Rifle) - السعر: 500 💰 " + (weapons[2].owned ? "[تم الشراء]" : "[شراء]"), 130, startY + 60, paint);
            canvas.drawText("3. قاذف صواريخ (Rocket) - السعر: 1000 💰 " + (weapons[3].owned ? "[تم الشراء]" : "[شراء]"), 130, startY + 120, paint);
            canvas.drawText("4. شراء 3 قنابل إضافية - السعر: 80 💰 [شراء]", 130, startY + 180, paint);
            canvas.drawText("5. درع كامل 100% - السعر: 100 💰 [شراء]", 130, startY + 240, paint);
            canvas.drawText("6. علاج كامل للصحة - السعر: 75 💰 [شراء]", 130, startY + 300, paint);

            // زر إغلاق المتجر
            paint.setColor(Color.RED);
            canvas.drawRect(screenW / 2f - 120, screenH - 160, screenW / 2f + 120, screenH - 100, paint);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("إغلاق ❌", screenW / 2f, screenH - 120, paint);
        }

        // =========================================================================
        // التحكم باللمس والمدخلات (Touch Input Handling)
        // =========================================================================
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            float x = event.getX();
            float y = event.getY();

            if (isDead) {
                if (action == MotionEvent.ACTION_DOWN) {
                    playerHealth = playerMaxHealth;
                    playerArmor = 50;
                    isDead = false;
                    loadStage(currentStage);
                }
                return true;
            }

            // التعامل مع المتجر
            if (isShopOpen) {
                if (action == MotionEvent.ACTION_DOWN) {
                    handleShopClick(x, y);
                }
                return true;
            }

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    touchStartX = x;
                    touchStartY = y;

                    // فحص ضغط الأزرار
                    if (x > screenW / 2f - 90 && x < screenW / 2f + 90 && y < 90) {
                        isShopOpen = true;
                        return true;
                    }
                    if (Math.hypot(x - (screenW - 130), y - (screenH - 130)) < 85) {
                        isFiring = true;
                        fireCurrentWeapon();
                        return true;
                    }
                    if (Math.hypot(x - (screenW - 290), y - (screenH - 110)) < 65) {
                        throwGrenade();
                        return true;
                    }
                    if (Math.hypot(x - (screenW - 120), y - (screenH - 290)) < 60) {
                        switchNextWeapon();
                        return true;
                    }
                    if (Math.hypot(x - (screenW - 250), y - (screenH - 250)) < 55) {
                        startReload();
                        return true;
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    float dx = x - touchStartX;
                    float dy = y - touchStartY;

                    // الجانب الأيسر للحركة (عصا التحكم)
                    if (touchStartX < screenW * 0.45f) {
                        moveForward = -dy / 50f;
                        moveStrafe = dx / 50f;
                        if (moveForward > 1.2) moveForward = 1.2f;
                        if (moveForward < -1.2) moveForward = -1.2f;
                        if (moveStrafe > 1.2) moveStrafe = 1.2f;
                        if (moveStrafe < -1.2) moveStrafe = -1.2f;
                    } else {
                        // الجانب الأيمن لتدوير الرؤية يمين ويسار
                        turnAngle = dx / 35f;
                        touchStartX = x;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    moveForward = 0;
                    moveStrafe = 0;
                    turnAngle = 0;
                    isFiring = false;
                    break;
            }
            return true;
        }

        private void handleShopClick(float x, float y) {
            float startY = 230;
            // شراء شوزن
            if (y > startY - 30 && y < startY + 20 && !weapons[1].owned && playerCoins >= weapons[1].cost) {
                playerCoins -= weapons[1].cost;
                weapons[1].owned = true;
                playTone(ToneGenerator.TONE_PROP_ACK);
            }
            // شراء رشاش
            else if (y > startY + 30 && y < startY + 80 && !weapons[2].owned && playerCoins >= weapons[2].cost) {
                playerCoins -= weapons[2].cost;
                weapons[2].owned = true;
                playTone(ToneGenerator.TONE_PROP_ACK);
            }
            // شراء قاذف صواريخ
            else if (y > startY + 90 && y < startY + 140 && !weapons[3].owned && playerCoins >= weapons[3].cost) {
                playerCoins -= weapons[3].cost;
                weapons[3].owned = true;
                playTone(ToneGenerator.TONE_PROP_ACK);
            }
            // شراء قنابل
            else if (y > startY + 150 && y < startY + 200 && playerCoins >= 80) {
                playerCoins -= 80;
                grenadeCount += 3;
                playTone(ToneGenerator.TONE_PROP_ACK);
            }
            // شراء درع
            else if (y > startY + 210 && y < startY + 260 && playerCoins >= 100) {
                playerCoins -= 100;
                playerArmor = 100;
                playTone(ToneGenerator.TONE_PROP_ACK);
            }
            // شراء علاج
            else if (y > startY + 270 && y < startY + 320 && playerCoins >= 75) {
                playerCoins -= 75;
                playerHealth = playerMaxHealth;
                playTone(ToneGenerator.TONE_PROP_ACK);
            }
            // زر الإغلاق
            if (x > screenW / 2f - 120 && x < screenW / 2f + 120 && y > screenH - 160 && y < screenH - 100) {
                isShopOpen = false;
            }
        }

        // =========================================================================
        // القتال، الأسلحة، والقنابل (Combat Actions)
        // =========================================================================
        private void fireCurrentWeapon() {
            Weapon w = weapons[currentWeaponIndex];
            long now = System.currentTimeMillis();
            if (now - w.lastFired < w.fireRateMs) return;
            if (isReloading) return;

            if (w.ammo <= 0) {
                startReload();
                return;
            }

            w.ammo--;
            w.lastFired = now;
            playTone(ToneGenerator.TONE_CDMA_KEYPAD_VOLUME_KEY_LITE);

            // خلق تأثير شرارة على الشاشة
            for (int i = 0; i < 8; i++) {
                particles.add(new Particle(screenW / 2f, screenH / 2f,
                        (float) ((Math.random() - 0.5) * 12), (float) ((Math.random() - 0.5) * 12),
                        Color.YELLOW, 10));
            }

            // فحص إصابة الأعداء في منتصف الشاشة مباشرة
            for (Enemy enemy : enemies) {
                if (enemy.isDead) continue;
                double spriteX = enemy.x - posX;
                double spriteY = enemy.y - posY;
                double invDet = 1.0 / (planeX * dirY - dirX * planeY);
                double transformY = invDet * (-planeY * spriteX + planeX * spriteY);
                double transformX = invDet * (dirY * spriteX - dirX * spriteY);

                if (transformY > 0) {
                    int spriteScreenX = (int) ((RENDER_W / 2) * (1 + transformX / transformY));
                    if (Math.abs(spriteScreenX - RENDER_W / 2) < 25 && transformY < zBuffer[RENDER_W / 2]) {
                        damageEnemy(enemy, w.damage);
                        break;
                    }
                }
            }
        }

        private void throwGrenade() {
            if (grenadeCount <= 0) return;
            grenadeCount--;
            playTone(ToneGenerator.TONE_DTMF_D);
            activeGrenades.add(new Grenade(posX, posY, dirX * 2.5, dirY * 2.5));
        }

        private void explodeGrenade(Grenade g) {
            playTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE);
            // تأثير انفجار
            for (int i = 0; i < 40; i++) {
                particles.add(new Particle(screenW / 2f, screenH / 2f,
                        (float) ((Math.random() - 0.5) * 35), (float) ((Math.random() - 0.5) * 35),
                        Color.RED, 25));
            }

            // ضرر جماعي (Splash Damage)
            for (Enemy enemy : enemies) {
                if (enemy.isDead) continue;
                double dist = Math.hypot(enemy.x - g.x, enemy.y - g.y);
                if (dist < 3.5) {
                    damageEnemy(enemy, (int) (140 * (1 - dist / 3.5)));
                }
            }
        }

        private void damageEnemy(Enemy enemy, int amount) {
            enemy.health -= amount;
            if (enemy.health <= 0) {
                enemy.isDead = true;
                playerCoins += enemy.reward;
                playTone(ToneGenerator.TONE_PROP_PROMPT);
            }
        }

        private void takePlayerDamage(int damage) {
            if (playerArmor > 0) {
                playerArmor -= damage;
                if (playerArmor < 0) {
                    playerHealth += playerArmor;
                    playerArmor = 0;
                }
            } else {
                playerHealth -= damage;
            }

            playTone(ToneGenerator.TONE_PROP_BEEP2);
            if (playerHealth <= 0) {
                playerHealth = 0;
                isDead = true;
            }
        }

        private void switchNextWeapon() {
            for (int i = 1; i <= weapons.length; i++) {
                int next = (currentWeaponIndex + i) % weapons.length;
                if (weapons[next].owned) {
                    currentWeaponIndex = next;
                    isReloading = false;
                    playTone(ToneGenerator.TONE_PROP_BEEP);
                    break;
                }
            }
        }

        private void startReload() {
            Weapon w = weapons[currentWeaponIndex];
            if (w.ammo < w.maxClip && w.totalAmmo > 0 && !isReloading) {
                isReloading = true;
                reloadStartTime = System.currentTimeMillis();
                playTone(ToneGenerator.TONE_SUP_RINGTONE);
            }
        }

        private void playTone(int toneType) {
            try {
                if (toneGen != null) toneGen.startTone(toneType, 80);
            } catch (Exception ignored) {}
        }
    }
}