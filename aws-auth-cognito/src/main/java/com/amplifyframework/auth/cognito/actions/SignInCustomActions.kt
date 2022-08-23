/*
 * Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amplifyframework.auth.cognito.actions

import aws.sdk.kotlin.services.cognitoidentityprovider.model.AuthFlowType
import aws.sdk.kotlin.services.cognitoidentityprovider.model.ChallengeNameType
import com.amplifyframework.auth.AuthException
import com.amplifyframework.auth.cognito.AuthEnvironment
import com.amplifyframework.auth.cognito.helpers.AuthHelper
import com.amplifyframework.auth.cognito.helpers.SignInChallengeHelper
import com.amplifyframework.statemachine.Action
import com.amplifyframework.statemachine.codegen.actions.CustomSignInActions
import com.amplifyframework.statemachine.codegen.events.AuthenticationEvent
import com.amplifyframework.statemachine.codegen.events.CustomSignInEvent

object SignInCustomActions : CustomSignInActions {
    override fun initiateCustomSignInAuthAction(event: CustomSignInEvent.EventType.InitiateCustomSignIn): Action =
        Action<AuthEnvironment>("InitCustomAuth") { id, dispatcher ->
            logger?.verbose("$id Starting execution")
            val evt = try {
                val secretHash = try {
                    AuthHelper().getSecretHash(
                        event.username,
                        configuration.userPool?.appClient,
                        configuration.userPool?.appClientSecret
                    )
                } catch (e: java.lang.Exception) {
                    null
                }

                var authParams = mapOf("USERNAME" to event.username)
                secretHash?.also { authParams = authParams.plus("SECRET_HASH" to secretHash) }

                val initiateAuthResponse = cognitoAuthService.cognitoIdentityProviderClient?.initiateAuth {
                    authFlow = AuthFlowType.CustomAuth
                    clientId = configuration.userPool?.appClient
                    authParameters = authParams
                }

                if (initiateAuthResponse?.challengeName == ChallengeNameType.CustomChallenge &&
                    initiateAuthResponse.challengeParameters != null
                ) {
                    SignInChallengeHelper.evaluateNextStep(
                        userId = "",
                        username = event.username,
                        challengeNameType = initiateAuthResponse.challengeName,
                        session = initiateAuthResponse.session,
                        challengeParameters = initiateAuthResponse.challengeParameters,
                        authenticationResult = initiateAuthResponse.authenticationResult
                    )
                } else {
                    val errorEvent = CustomSignInEvent(
                        CustomSignInEvent.EventType.ThrowAuthError(
                            AuthException(
                                "This sign in method is not supported",
                                "Please consult our docs for supported sign in methods"
                            )
                        )
                    )
                    logger?.verbose("$id Sending event ${errorEvent.type}")
                    dispatcher.send(errorEvent)
                    AuthenticationEvent(AuthenticationEvent.EventType.CancelSignIn())
                }
            } catch (e: Exception) {
                val errorEvent = CustomSignInEvent(CustomSignInEvent.EventType.ThrowAuthError(e))
                logger?.verbose("$id Sending event ${errorEvent.type}")
                dispatcher.send(errorEvent)
                AuthenticationEvent(AuthenticationEvent.EventType.CancelSignIn())
            }
            logger?.verbose("$id Sending event ${evt.type}")
            dispatcher.send(evt)
        }
}
