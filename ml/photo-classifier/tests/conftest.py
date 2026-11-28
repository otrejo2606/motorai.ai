import pytest

from tests.helpers import DEFAULT_PREDICTIONS, FakeClassifier


@pytest.fixture
def fake() -> FakeClassifier:
    return FakeClassifier(DEFAULT_PREDICTIONS)
