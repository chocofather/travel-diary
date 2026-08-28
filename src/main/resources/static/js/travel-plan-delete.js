/*
  여행 계획 삭제.

  방장이 방을 통째로 접는 일이다. 되돌릴 수 없어서 누르자마자 지우지 않고
  한 번 물어본다. 실제로 지우는 것은 확인 창 안의 form 이고,
  지울 수 있는지는 그때 서버가 다시 본다(화면에 버튼이 보였다는 것은 근거가 아니다).

  여기서 하는 일은 둘뿐이다.
  확인 창을 열고 닫는 것과, 남이 지운 방에서 빠져나오는 것.
*/
document.addEventListener("DOMContentLoaded", () => {
    /*
      진입점과 확인 창은 둘 다 방장 전용 조각에 있고, 그 조각은 방장이 바뀌면
      통째로 갈린다. 버튼을 한 번 찾아 두면 갈린 뒤에는 눌리지 않으므로,
      문서에서 한 번만 듣고 눌린 자리를 그때 확인한다. 창도 열 때마다 다시 찾는다.
      이 방식이면 조각이 몇 번 갈려도 다시 붙일 것이 없고, 동작이 겹쳐 쌓이지도 않는다.

      다만 이 듣는 자리는 문서다. 도중에 누가 이벤트를 멈추면 여기까지 오지 못한다.
      참여자·초대 팝오버의 패널이 그렇다 —— 바깥을 눌러야 닫히도록
      패널 안쪽 클릭에 stopPropagation 을 걸어 두었다.
      그래서 이 버튼은 그 패널 안이 아니라 상단 액션 줄에 있어야 한다.
    */
    function modalOf() {
        return document.querySelector("[data-travel-plan-plan-delete-modal]");
    }

    function openModal() {
        const modal = modalOf();
        // 넘겨준 사람 화면에는 이 창이 없다. 아무 일도 하지 않는다.
        if (!modal) return;
        modal.hidden = false;
        modal.querySelector("[data-travel-plan-plan-delete-cancel]")?.focus();
    }

    function closeModal() {
        const modal = modalOf();
        if (modal) modal.hidden = true;
    }

    document.addEventListener("click", event => {
        const target = event.target;
        if (!(target instanceof Element)) return;

        if (target.closest("[data-travel-plan-plan-delete-open]")) {
            openModal();
            return;
        }
        if (target.closest("[data-travel-plan-plan-delete-cancel]")) {
            closeModal();
            return;
        }
        // 창 바깥의 어두운 곳을 누르면 닫는다. 창 안쪽 클릭은 그대로 둔다.
        const modal = modalOf();
        if (modal && !modal.hidden && target === modal) closeModal();
    });

    document.addEventListener("keydown", event => {
        if (event.key !== "Escape") return;
        const modal = modalOf();
        if (modal && !modal.hidden) closeModal();
    });

    /*
      방장이 방을 지웠다.

      지운 본인은 form 응답을 따라 목록으로 간다(서버가 보낸다).
      이 알림을 쓰는 것은 같은 방을 열어 두고 있던 다른 사람들이다.
      더 이상 있지도 않은 방을 보고 있을 이유가 없으므로 새로고침 없이 목록으로 나온다.
      같은 알림이 두 번 와도 한 번만 움직인다.
    */
    let leaving = false;

    document.addEventListener("travelplan:plan-deleted", () => {
        if (leaving) return;
        leaving = true;
        window.location.href = "/travel-plans";
    });
});
