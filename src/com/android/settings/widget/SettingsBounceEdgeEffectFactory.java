/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.EdgeEffect;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Adds a subtle resistance and bounce treatment to Settings recycler views.
 */
public class SettingsBounceEdgeEffectFactory extends RecyclerView.EdgeEffectFactory {

    private static final float MAX_PULL_TRANSLATION_DP = 32f;
    private static final float MAX_ABSORB_TRANSLATION_DP = 40f;
    private static final float PULL_DISTANCE_AMPLIFICATION = 4f;
    private static final float RESISTANCE_POWER = 1.2f;
    private static final float MAX_ABSORB_VELOCITY = 12_000f;
    private static final long RELEASE_DURATION_MS = 220L;
    private static final long MIN_ABSORB_DURATION_MS = 80L;
    private static final long MAX_ABSORB_DURATION_MS = 140L;
    private static final Interpolator SETTLE_INTERPOLATOR =
            new PathInterpolator(0.2f, 0f, 0f, 1f);

    @NonNull
    @Override
    protected EdgeEffect createEdgeEffect(@NonNull RecyclerView view, int direction) {
        if (direction != DIRECTION_TOP && direction != DIRECTION_BOTTOM) {
            return new EdgeEffect(view.getContext());
        }
        return new BounceEdgeEffect(view, direction);
    }

    @VisibleForTesting
    static float calculateResistedShift(float normalizedPullDistance, float maxTranslationPx) {
        float sign = Math.signum(normalizedPullDistance);
        float clampedDistance = Math.min(1f, Math.abs(normalizedPullDistance));
        float easedDistance = 1f - (float) Math.pow(1f - clampedDistance, RESISTANCE_POWER);
        return sign * maxTranslationPx * easedDistance;
    }

    @VisibleForTesting
    static float calculateAbsorbPeakShift(int velocity, float maxTranslationPx) {
        float normalizedVelocity = Math.min(1f, Math.abs(velocity) / MAX_ABSORB_VELOCITY);
        float easedVelocity = 1f - (float) Math.pow(1f - normalizedVelocity, RESISTANCE_POWER);
        return maxTranslationPx * easedVelocity;
    }

    @VisibleForTesting
    static long calculateAbsorbPeakDurationMillis(int velocity) {
        float normalizedVelocity = Math.min(1f, Math.abs(velocity) / MAX_ABSORB_VELOCITY);
        return (long) (MAX_ABSORB_DURATION_MS
                - ((MAX_ABSORB_DURATION_MS - MIN_ABSORB_DURATION_MS) * normalizedVelocity));
    }

    private static float dpToPx(Context context, float valueDp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                valueDp,
                context.getResources().getDisplayMetrics());
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    private static final class BounceEdgeEffect extends EdgeEffect {
        private final RecyclerView mRecyclerView;
        private final float mDirectionMultiplier;
        private final float mMaxPullTranslationPx;
        private final float mMaxAbsorbTranslationPx;

        private Animator mTranslationAnimator;
        private float mAccumulatedPullDistance;

        BounceEdgeEffect(@NonNull RecyclerView recyclerView, int direction) {
            super(recyclerView.getContext());
            mRecyclerView = recyclerView;
            mDirectionMultiplier = direction == DIRECTION_TOP ? 1f : -1f;
            mMaxPullTranslationPx = dpToPx(recyclerView.getContext(), MAX_PULL_TRANSLATION_DP);
            mMaxAbsorbTranslationPx = dpToPx(recyclerView.getContext(), MAX_ABSORB_TRANSLATION_DP);
        }

        @Override
        public void onPull(float deltaDistance) {
            handlePull(deltaDistance);
        }

        @Override
        public void onPull(float deltaDistance, float displacement) {
            handlePull(deltaDistance);
        }

        @Override
        public float onPullDistance(float deltaDistance, float displacement) {
            handlePull(deltaDistance);
            return deltaDistance;
        }

        private void handlePull(float deltaDistance) {
            if (deltaDistance == 0f) {
                return;
            }
            cancelAnimation();
            mAccumulatedPullDistance = clamp(
                    mAccumulatedPullDistance
                            + (deltaDistance * mDirectionMultiplier * PULL_DISTANCE_AMPLIFICATION),
                    -1f,
                    1f);
            mRecyclerView.setTranslationY(
                    calculateResistedShift(mAccumulatedPullDistance, mMaxPullTranslationPx));
        }

        @Override
        public void onRelease() {
            mAccumulatedPullDistance = 0f;
            animateToRest(RELEASE_DURATION_MS);
        }

        @Override
        public void onAbsorb(int velocity) {
            mAccumulatedPullDistance = 0f;
            float startTranslation = mRecyclerView.getTranslationY();
            float targetTranslation = clamp(
                    startTranslation + (mDirectionMultiplier
                            * calculateAbsorbPeakShift(velocity, mMaxAbsorbTranslationPx)),
                    -mMaxAbsorbTranslationPx,
                    mMaxAbsorbTranslationPx);
            if (targetTranslation == startTranslation) {
                animateToRest(RELEASE_DURATION_MS);
                return;
            }
            cancelAnimation();
            AnimatorSet animatorSet = new AnimatorSet();
            ObjectAnimator peakAnimator = ObjectAnimator.ofFloat(
                    mRecyclerView,
                    View.TRANSLATION_Y,
                    startTranslation,
                    targetTranslation);
            peakAnimator.setDuration(calculateAbsorbPeakDurationMillis(velocity));
            peakAnimator.setInterpolator(SETTLE_INTERPOLATOR);

            ObjectAnimator settleAnimator = ObjectAnimator.ofFloat(
                    mRecyclerView,
                    View.TRANSLATION_Y,
                    targetTranslation,
                    0f);
            settleAnimator.setDuration(RELEASE_DURATION_MS);
            settleAnimator.setInterpolator(SETTLE_INTERPOLATOR);
            animatorSet.playSequentially(peakAnimator, settleAnimator);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mTranslationAnimator = null;
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    mTranslationAnimator = null;
                }
            });
            mTranslationAnimator = animatorSet;
            animatorSet.start();
        }

        @Override
        public boolean draw(@NonNull Canvas canvas) {
            return false;
        }

        @Override
        public boolean isFinished() {
            return mTranslationAnimator == null && mRecyclerView.getTranslationY() == 0f;
        }

        @Override
        public void finish() {
            cancelAnimation();
            mAccumulatedPullDistance = 0f;
            mRecyclerView.setTranslationY(0f);
        }

        private void animateToRest(long durationMillis) {
            if (mRecyclerView.getTranslationY() == 0f) {
                return;
            }
            cancelAnimation();
            ObjectAnimator animator = ObjectAnimator.ofFloat(
                    mRecyclerView,
                    View.TRANSLATION_Y,
                    mRecyclerView.getTranslationY(),
                    0f);
            animator.setDuration(durationMillis);
            animator.setInterpolator(SETTLE_INTERPOLATOR);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mTranslationAnimator = null;
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    mTranslationAnimator = null;
                }
            });
            mTranslationAnimator = animator;
            animator.start();
        }

        private void cancelAnimation() {
            if (mTranslationAnimator != null) {
                mTranslationAnimator.cancel();
                mTranslationAnimator = null;
            }
        }
    }
}