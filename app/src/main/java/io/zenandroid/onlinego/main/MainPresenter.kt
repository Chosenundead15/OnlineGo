package io.zenandroid.onlinego.main

import android.util.Log
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.zenandroid.onlinego.model.local.Game
import io.zenandroid.onlinego.ogs.ActiveGameRepository
import io.zenandroid.onlinego.ogs.OGSServiceImpl
import java.util.concurrent.TimeUnit

/**
 * Created by alex on 14/03/2018.
 */
class MainPresenter (val view : MainContract.View, private val activeGameRepository: ActiveGameRepository) : MainContract.Presenter {

    private val subscriptions = CompositeDisposable()
    private var lastGameNotified: Game? = null
    private var lastMoveCount: Int? = null

    override fun subscribe() {
        subscriptions.add(
                OGSServiceImpl.instance.loginWithToken().
                        subscribe ({
                            OGSServiceImpl.instance.ensureSocketConnected()
                            OGSServiceImpl.instance.resendAuth()
                        }, {
                            view.showLogin()
                        })

        )
        subscriptions.add(
                activeGameRepository.myMoveCountObservable
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::onMyMoveCountChanged)
        )
        subscriptions.add(
                Observable.interval(10, TimeUnit.SECONDS).subscribe {
                    OGSServiceImpl.instance.ensureSocketConnected()
                }
        )
        activeGameRepository.subscribe()
    }

    private fun onMyMoveCountChanged(myMoveCount: Int) {
        if (myMoveCount == 0) {
            view.notificationsButtonEnabled = false
            view.notificationsBadgeVisible = false
            view.cancelNotification()
        } else {
//            val sortedMyTurnGames = activeGameRepository.myTurnGamesList.sortedWith(compareBy { it.id })
            view.notificationsButtonEnabled = true
            view.notificationsBadgeVisible = true
            view.notificationsBadgeCount = myMoveCount.toString()
//            view.updateNotification(sortedMyTurnGames)
            lastMoveCount?.let {
                if(myMoveCount > it) {
                    view.vibrate()
                }
            }
        }
        lastMoveCount = myMoveCount
    }

    override fun unsubscribe() {
        activeGameRepository.unsubscribe()
        subscriptions.clear()
        OGSServiceImpl.instance.disconnect()
    }

    fun navigateToGameScreenById(gameId: Long) {
        subscriptions.add(
            activeGameRepository.getGameSingle(gameId)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(view::navigateToGameScreen, this::onError)
        )
    }

    override fun onNotificationClicked() {
        val gameToNavigate = if(lastGameNotified == null) {
            activeGameRepository.myTurnGamesList[0]
        } else {
            val index = activeGameRepository.myTurnGamesList.indexOfFirst { it.id == lastGameNotified?.id }
            if(index == -1) {
                activeGameRepository.myTurnGamesList[0]
            } else {
                activeGameRepository.myTurnGamesList[(index + 1) % activeGameRepository.myTurnGamesList.size]
            }
        }
        lastGameNotified = gameToNavigate
        view.navigateToGameScreen(gameToNavigate)
    }

    private fun onError(t: Throwable) {
        Log.e(MainActivity.TAG, t.message, t)
        view.showError(t.message)
    }
}