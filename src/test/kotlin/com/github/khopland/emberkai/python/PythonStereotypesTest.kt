package com.github.khopland.emberkai.python

import com.github.khopland.emberkai.IconProviderTestCase
import com.github.khopland.emberkai.core.BadgeSlot
import com.github.khopland.emberkai.core.IconDescriptions
import com.github.khopland.emberkai.core.IconResolver
import com.github.khopland.emberkai.core.fact
import com.github.khopland.emberkai.node.SourceStereotypeFact
import com.github.khopland.emberkai.node.StereotypeBadgeIcon
import com.github.khopland.emberkai.scopes.EmberkaiScope
import com.github.khopland.emberkai.settings.EmberkaiIconsSettings
import com.github.khopland.emberkai.settings.IconRuleCategory
import com.github.khopland.emberkai.stereotypes.ClassStereotype
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent

internal class PythonStereotypesTest : IconProviderTestCase() {
    private lateinit var settings: EmberkaiIconsSettings
    private var savedState: EmberkaiIconsSettings.State? = null

    override fun setUp() {
        super.setUp()
        settings = EmberkaiIconsSettings.getInstance()
        savedState = settings.state
    }

    override fun tearDown() {
        savedState?.let(settings::loadState)
        super.tearDown()
    }

    fun testFastApiRoutesOnRoutersTheModuleCreates() {
        manifest("api/pyproject.toml", "[project]\ndependencies = [\"fastapi[standard]>=0.115\"]\n")
        assertRole(
            ClassStereotype.WEB, "@router.get",
            "api/app/orders.py",
            """
            from fastapi import APIRouter

            router = APIRouter(prefix="/orders")

            @router.get("/{id}")
            async def order(id: int): ...
            """,
        )
        assertRole(
            ClassStereotype.WEB, "@app.post",
            "api/app/main.py",
            """
            import fastapi

            app = fastapi.FastAPI()

            @app.post("/checkout")
            def checkout(): ...
            """,
        )
    }

    fun testFlaskBlueprintsAndImportedApps() {
        manifest("web/requirements.txt", "Flask==3.1.0\nFlask_SQLAlchemy\n")
        assertRole(
            ClassStereotype.WEB, "@bp.route",
            "web/shop/views.py",
            """
            from flask import Blueprint as FlaskBlueprint
            bp = FlaskBlueprint("shop", __name__)

            @bp.route("/")
            def index(): ...
            """,
        )
        // Routes registered on an app created elsewhere, by its conventional name.
        assertRole(
            ClassStereotype.WEB, "@app.get",
            "web/shop/health.py",
            "from shop import app\n\n@app.get(\"/health\")\ndef health(): ...\n",
        )
    }

    fun testDecoratorsNeedTheirFrameworkObject() {
        manifest("api/pyproject.toml", "dependencies = [\"fastapi\"]\n")
        // A cache with a `get` method, a property, and a docstring example are not routes.
        assertNoRole(
            "api/app/cache.py",
            """
            from functools import lru_cache
            from app.store import cache

            @cache.get
            def cached(): ...

            def helper():
                '''
                @router.get("/x")
                '''
            # @app.get("/commented")
            """,
        )
        // Without any recognized framework in the manifest, sources are not read.
        manifest("tools/pyproject.toml", "dependencies = [\"requests\"]\n")
        assertNoRole("tools/main.py", "from fastapi import FastAPI\napp = FastAPI()\n@app.get('/')\ndef root(): ...\n")
    }

    fun testDjangoModelsViewsSignalsAndApps() {
        manifest("site/manage.py", "import os\nfrom django.core.management import execute_from_command_line\n")
        assertRole(
            ClassStereotype.ENTITY, "models.Model",
            "site/orders/models.py",
            "from django.db import models\n\nclass Order(models.Model):\n    total = models.IntegerField()\n",
        )
        assertRole(
            ClassStereotype.WEB, "APIView",
            "site/orders/views.py",
            "from rest_framework.views import APIView\n\nclass Orders(APIView):\n    pass\n",
        )
        assertRole(
            ClassStereotype.WEB, "generic.ListView",
            "site/orders/pages.py",
            "from django.views import generic\n\nclass OrderList(generic.ListView):\n    pass\n",
        )
        assertRole(
            ClassStereotype.WEB, "@api_view",
            "site/orders/api.py",
            "from rest_framework.decorators import api_view\n\n@api_view(['GET'])\ndef orders(request): ...\n",
        )
        assertRole(
            ClassStereotype.LISTENER, "@receiver",
            "site/orders/signals.py",
            "from django.dispatch import receiver\n\n@receiver(post_save)\ndef saved(sender, **kwargs): ...\n",
        )
        assertRole(
            ClassStereotype.CONFIGURATION, "AppConfig",
            "site/orders/apps.py",
            "from django.apps import AppConfig\n\nclass OrdersConfig(AppConfig):\n    name = 'orders'\n",
        )
    }

    fun testTaskQueuesSchedulersAndBrokers() {
        manifest("worker/pyproject.toml", "dependencies = [\"celery\", \"APScheduler\", \"faststream[kafka]\", \"dramatiq\"]\n")
        assertRole(
            ClassStereotype.LISTENER, "@shared_task",
            "worker/jobs/emails.py",
            "from celery import shared_task\n\n@shared_task\ndef send(): ...\n",
        )
        assertRole(
            ClassStereotype.LISTENER, "@app.task",
            "worker/jobs/reports.py",
            "from worker.celery import app\n\n@app.task(bind=True)\ndef report(self): ...\n",
        )
        assertRole(
            ClassStereotype.LISTENER, "@actor",
            "worker/jobs/thumbnails.py",
            "import dramatiq\n\n@dramatiq.actor\ndef thumbnail(): ...\n",
        )
        assertRole(
            ClassStereotype.SCHEDULED, "@scheduler.scheduled_job",
            "worker/jobs/nightly.py",
            """
            from apscheduler.schedulers.blocking import BlockingScheduler
            scheduler = BlockingScheduler()

            @scheduler.scheduled_job("cron", hour=3)
            def nightly(): ...
            """,
        )
        assertRole(
            ClassStereotype.LISTENER, "@broker.subscriber",
            "worker/jobs/events.py",
            "from faststream.kafka import KafkaBroker\nbroker = KafkaBroker()\n\n@broker.subscriber(\"orders\")\nasync def on_order(body): ...\n",
        )
    }

    fun testEntitiesAndSettings() {
        manifest("svc/pyproject.toml", "dependencies = [\"sqlalchemy>=2\", \"sqlmodel\", \"pydantic-settings\"]\n")
        assertRole(
            ClassStereotype.ENTITY, "__tablename__",
            "svc/db/models.py",
            """
            from sqlalchemy.orm import Mapped, mapped_column
            from svc.db import Base

            class Order(Base):
                __tablename__ = "orders"
                id: Mapped[int] = mapped_column(primary_key=True)
            """,
        )
        assertRole(
            ClassStereotype.ENTITY, "SQLModel, table=True",
            "svc/db/heroes.py",
            "from sqlmodel import SQLModel\n\nclass Hero(SQLModel, table=True):\n    id: int\n",
        )
        assertNoRole("svc/db/schemas.py", "from sqlmodel import SQLModel\n\nclass HeroRead(SQLModel):\n    id: int\n")
        assertRole(
            ClassStereotype.ENTITY, "SQLModel, table=True",
            "svc/db/generic.py",
            "from sqlmodel import SQLModel\n\nclass Tagged[T](SQLModel, table=True):\n    id: int\n",
        )
        assertRole(
            ClassStereotype.CONFIGURATION, "BaseSettings",
            "svc/config.py",
            "from pydantic_settings import BaseSettings\n\nclass Settings(BaseSettings):\n    url: str\n",
        )
    }

    fun testTestKindsCountOnlyInTestModules() {
        manifest("svc/pyproject.toml", "[dependency-groups]\ndev = [\"testcontainers[postgres]\", \"fastapi\", \"django\"]\n")
        assertRole(
            ClassStereotype.CONTAINER_TEST, "testcontainers",
            "svc/tests/conftest.py",
            "from testcontainers.postgres import PostgresContainer\nfrom fastapi.testclient import TestClient\n",
        )
        assertRole(
            ClassStereotype.APPLICATION_TEST, "TestClient",
            "svc/tests/test_orders.py",
            "from fastapi.testclient import TestClient\nfrom svc.main import app\n\nclient = TestClient(app)\n",
        )
        assertRole(
            ClassStereotype.APPLICATION_TEST, "TestCase",
            "svc/orders/orders_test.py",
            "from django.test import TestCase\n\nclass OrderTest(TestCase):\n    pass\n",
        )
        assertNoRole("svc/scripts/local_db.py", "from testcontainers.postgres import PostgresContainer\n")
    }

    fun testInstalledPackagesAreIgnored() {
        manifest("pyproject.toml", "dependencies = [\"fastapi\"]\n")
        assertNoRole(
            ".venv/lib/python3.13/site-packages/fastapi/routing.py",
            "from fastapi import APIRouter\nrouter = APIRouter()\n@router.get('/')\ndef x(): ...\n",
        )
    }

    fun testBadgeIsDrawnTopLeftAndFollowsTheSetting() {
        manifest("api/pyproject.toml", "dependencies = [\"fastapi\"]\n")
        val routes = file("api/app/orders.py", "from fastapi import APIRouter\nrouter = APIRouter()\n\n@router.get('/')\ndef orders(): ...\n")

        val resolution = IconResolver.resolveWithDetails(routes, project)
        val badge = resolution.icon as? StereotypeBadgeIcon
        assertNotNull("A FastAPI router module gets the stereotype glyph", badge)
        assertEquals(ClassStereotype.WEB, badge!!.stereotype)
        assertEquals(BadgeSlot.ROLE, badge.slot)
        assertEquals("web controller (@router.get)", resolution.fact<SourceStereotypeFact>()?.label)
        assertEquals("Web controller (@router.get)", IconDescriptions.describe(routes, resolution))

        settings.setEnabled(IconRuleCategory.STEREOTYPE_BADGES, false)
        assertFalse(IconResolver.resolve(routes, project) is StereotypeBadgeIcon)
    }

    fun testEntryPointsScopeIncludesRoutesAndTasks() {
        manifest("svc/pyproject.toml", "dependencies = [\"fastapi\", \"celery\", \"sqlalchemy\"]\n")
        val routes = file("svc/api.py", "from fastapi import FastAPI\napp = FastAPI()\n\n@app.get('/')\ndef root(): ...\n")
        val task = file("svc/tasks.py", "from celery import shared_task\n\n@shared_task\ndef work(): ...\n")
        val model = file("svc/models.py", "import sqlalchemy\n\nclass Order(Base):\n    __tablename__ = 'orders'\n")

        assertTrue(EmberkaiScope.ENTRY_POINTS.contains(routes, project))
        assertTrue(EmberkaiScope.ENTRY_POINTS.contains(task, project))
        assertFalse("Entities are not entry points", EmberkaiScope.ENTRY_POINTS.contains(model, project))
    }

    fun testManifestChangesRepaintIcons() {
        val listener = PythonProjectListener()
        val pyproject = manifest("svc/pyproject.toml", "dependencies = []\n")
        val module = file("svc/main.py", "print('hi')\n")
        assertTrue(listener.isRelevant(listOf(VFileContentChangeEvent(null, pyproject, 0, 0))))
        assertFalse(listener.isRelevant(listOf(VFileContentChangeEvent(null, module, 0, 0))))
    }

    fun testFrameworksAreReadFromAnyManifestSpelling() {
        val found = PythonProjects.frameworksIn(
            """
            [tool.poetry.dependencies]
            Django = "^5.1"
            celery = {extras = ["redis"], version = "^5.4"}
            pydantic_settings = "*"
            django-environ = "*"
            """.trimIndent(),
        )
        assertEquals(setOf(PythonFramework.DJANGO, PythonFramework.CELERY, PythonFramework.PYDANTIC_SETTINGS), found)
    }

    private fun assertRole(stereotype: ClassStereotype, evidence: String, path: String, source: String) {
        val match = PythonStereotypes.match(file(path, source.trimIndent() + "\n"), project)
        assertEquals(path, stereotype to evidence, match?.stereotype to match?.evidence)
    }

    private fun assertNoRole(path: String, source: String) {
        assertNull(path, PythonStereotypes.match(file(path, source.trimIndent() + "\n"), project))
    }

    private fun manifest(path: String, content: String): VirtualFile = file(path, content)

    private fun file(path: String, content: String): VirtualFile = myFixture.addFileToProject(path, content).virtualFile
}
